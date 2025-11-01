import pulumi
import pulumi_gcp as gcp
import os
import subprocess
from dotenv import load_dotenv
from pathlib import Path

# Load environment variables from .env file
load_dotenv("../.env")

# Get configuration
config = pulumi.Config()
project_id = os.getenv("GCP_PROJECT_ID")
region = os.getenv("GCP_REGION", "europe-west1")  # Belgium - cheapest European region
function_name = os.getenv("FUNCTION_NAME", "tg-journals-function")
database_url = os.getenv("SUPABASE_DATABASE_URL", "")
supabase_ca_path = Path("supabase_prod-ca-2021.crt")
supabase_ca_content = ""
if supabase_ca_path.exists():
    supabase_ca_content = supabase_ca_path.read_text()

# Validate required environment variables
if not project_id:
    raise ValueError("GCP_PROJECT_ID must be set in .env file")

# Create Artifact Registry repository for Docker images
artifact_registry_repo = gcp.artifactregistry.Repository(
    "tg-journals-registry",
    repository_id="tg-journals",
    format="DOCKER",
    location=region,
    description="Docker repository for tg-journals Cloud Run service"
)

# Docker image configuration
# Artifact Registry format: {region}-docker.pkg.dev/{project_id}/{repository_id}/{image_name}
image_name_base = f"{region}-docker.pkg.dev/{project_id}/tg-journals/tg-journals"
image_tag = "latest"  # FYI: not good but easiest to maintain.

# Create a service account for the Cloud Function
service_account = gcp.serviceaccount.Account(
    "tg-journals-sa",
    account_id="tg-journals-sa",
    display_name="Telegram Journals Bot Service Account",
    description="Service account for Telegram Journals Bot Cloud Run v2"
)

# Grant necessary permissions to the service account
gcp.projects.IAMMember(
    "tg-journals-sa-logging",
    project=project_id,
    role="roles/logging.logWriter",
    member=service_account.email.apply(lambda email: f"serviceAccount:{email}")
)

gcp.projects.IAMMember(
    "tg-journals-sa-monitoring",
    project=project_id,
    role="roles/monitoring.metricWriter",
    member=service_account.email.apply(lambda email: f"serviceAccount:{email}")
)

# Use locally built Docker image.
# We need to wait for the registry to be created before pushing
def push_local_image():
    """Push locally built Docker image to Artifact Registry"""
    try:
        # Check if local image exists
        local_image = "tg-journals:latest"
        check_cmd = ["docker", "images", "-q", local_image]
        result = subprocess.run(check_cmd, capture_output=True, text=True)
        
        if not result.stdout.strip():
            raise Exception(f"Local Docker image '{local_image}' not found. Please build it first with: make docker-build")
        
        # Tag for Artifact Registry
        full_image_name = f"{image_name_base}:{image_tag}"
        tag_cmd = ["docker", "tag", local_image, full_image_name]
        subprocess.run(tag_cmd, check=True)
        
        # Configure Docker for Artifact Registry
        auth_cmd = ["gcloud", "auth", "configure-docker", f"{region}-docker.pkg.dev"]
        subprocess.run(auth_cmd, check=True)
        
        # Push to Artifact Registry
        push_cmd = ["docker", "push", full_image_name]
        subprocess.run(push_cmd, check=True)
        
        return full_image_name
    except subprocess.CalledProcessError as e:
        raise Exception(f"Failed to push local Docker image: {e}")

# Push the locally built Docker image after registry is created
docker_image = artifact_registry_repo.name.apply(lambda _: push_local_image())

# Note: Cloud Functions provide HTTPS by default without additional cost.

# Deploy to Cloud Run v2 using the pushed container image
run_service = gcp.cloudrunv2.Service(
    "tg-journals-service",
    name=function_name,
    location=region,
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        service_account=service_account.email,
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=docker_image,
                envs=[
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="TELEGRAM_BOT_TOKEN", value=os.getenv("TELEGRAM_BOT_TOKEN", "")),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="TELEGRAM_WEBHOOK_SECRET", value=os.getenv("TELEGRAM_WEBHOOK_SECRET", "")),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="SPRING_PROFILES_ACTIVE", value="production"),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="DATABASE_URL", value=database_url),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="SUPABASE_CA_CERT", value=supabase_ca_content),
                ],
                resources=gcp.cloudrunv2.ServiceTemplateContainerResourcesArgs(
                    limits={
                        "memory": "256Mi",
                        "cpu": "1"
                    }
                )
            )
        ],
        scaling=gcp.cloudrunv2.ServiceTemplateScalingArgs(
            min_instance_count=0,
            max_instance_count=1
        ),
        timeout="60s"
    ),
    ingress="INGRESS_TRAFFIC_ALL"
)

# Allow unauthenticated invocations
run_iam = gcp.cloudrunv2.ServiceIamBinding(
    "tg-journals-run-iam",
    location=run_service.location,
    name=run_service.name,
    role="roles/run.invoker",
    members=["allUsers"]
)

# Export URL and basic info
pulumi.export("service_url", run_service.uri)
pulumi.export("service_name", run_service.name)
pulumi.export("service_account_email", service_account.email)
pulumi.export("registry_name", artifact_registry_repo.name)
pulumi.export("region", region)
