import pulumi
import pulumi_gcp as gcp
import os
import subprocess
from dotenv import load_dotenv
from pathlib import Path
from datetime import datetime, timezone

# Load environment variables from .env file
# Use interpolate=False to prevent variable expansion, override=True to overwrite Make-expanded values
# NOTE: Make's include .env expands $ variables, so we need override=True to get correct values from file
load_dotenv("../.env", interpolate=False, override=True)

# Get configuration
config = pulumi.Config()
project_id = os.getenv("GCP_PROJECT_ID")
region = os.getenv("GCP_REGION", "europe-west1")  # Belgium - cheapest European region
function_name = os.getenv("FUNCTION_NAME", "tg-journals-function")
# Get database configuration for production (Cloud Run)
# Use DB_PROD_* variables only (production deployment)
db_host = os.getenv("DB_PROD_HOST", "not_set_in_env")
db_port = os.getenv("DB_PROD_PORT", "not_set_in_env")
db_name = os.getenv("DB_PROD_NAME", "not_set_in_env")
db_username = os.getenv("DB_PROD_USERNAME", "not_set_in_env").strip("'")
# Strip quotes from password if present (use single quotes in .env file to prevent $ expansion)
# Single quotes in .env file will be included in os.getenv() value, so we strip them
db_password = os.getenv("DB_PROD_PASSWORD", "").strip("'")
if not db_password:
    raise ValueError("DB_PROD_PASSWORD must be set in .env file for production deployment")
# For Supabase, add SSL parameters - strip quotes if present
db_ssl_params_raw = os.getenv("DB_PROD_SSL_PARAMS")
# Remove surrounding quotes from both ends (handles both double and single quotes)
# Also strip any whitespace and ensure it starts with ?
if db_ssl_params_raw:
    db_ssl_params = db_ssl_params_raw.strip().strip("'")
    # Ensure it starts with ? if it doesn't already
    if not db_ssl_params.startswith('?'):
        db_ssl_params = '?' + db_ssl_params

supabase_ca_path = Path("supabase_prod-ca-2021.crt")
supabase_ca_content = ""
if supabase_ca_path.exists():
    supabase_ca_content = supabase_ca_path.read_text()

# Validate required environment variables
if not project_id:
    raise ValueError("GCP_PROJECT_ID must be set in .env file")
if not db_host or not db_password:
    raise ValueError("DB_PROD_HOST and DB_PROD_PASSWORD must be set in .env file for production deployment")
print(f"GCP_PROJECT_ID: {project_id}, GCP_REGION: {region}, FUNCTION_NAME: {function_name}, DB_HOST: {db_host}, DB_PORT: {db_port}, DB_NAME: {db_name}, DB_USERNAME: {db_username}, len(DB_PASSWORD): {len(db_password)}, DB_SSL_PARAMS: {db_ssl_params}")

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
# Use a unique tag based on UTC datetime with milliseconds, docker-tag safe
_now = datetime.now(timezone.utc)
_ms = f"{int(_now.microsecond/1000):03d}"
image_tag = f"{_now.strftime('%Y%m%dt%H%M%S')}{_ms}"

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
    """Push locally built Docker image to Artifact Registry and return versioned tag reference"""
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


def cleanup_old_images_after_deploy(_: str) -> str:
    """Remove older image tags keeping only the 3 most recent. Runs after deploy succeeds."""
    try:
        # List tags for this specific image, newest first
        image_path = f"{region}-docker.pkg.dev/{project_id}/tg-journals/tg-journals"
        list_cmd = [
            "gcloud", "artifacts", "docker", "images", "list",
            image_path,
            "--format=csv[no-heading](NAME,CREATE_TIME)",
            "--include-tags"
        ]
        list_result = subprocess.run(list_cmd, capture_output=True, text=True, check=True)
        rows = [line.strip() for line in list_result.stdout.splitlines() if line.strip()]
        # Keep only tag rows (exclude digest-only entries). NAME should be like <image>:<tag>
        image_rows = [r for r in rows if r.startswith(f"{image_name_base}:") and ":" in r]
        def parse_row(r):
            parts = r.split(',')
            return (parts[0], parts[1])
        # Sort by create time desc
        sorted_rows = sorted(image_rows, key=lambda r: parse_row(r)[1], reverse=True)
        keep = sorted_rows[:3]
        to_delete = sorted_rows[3:]
        deleted = []
        for r in to_delete:
            name, _ = parse_row(r)
            try:
                print(f"Deleting old image tag: {name}")
                del_cmd = ["gcloud", "artifacts", "docker", "images", "delete", name, "--quiet"]
                subprocess.run(del_cmd, check=True)
                deleted.append(name)
            except subprocess.CalledProcessError:
                pass
        if deleted:
            print("Deleted tags:")
            for n in deleted:
                print(f"  - {n}")
        else:
            print("No old image tags to delete (<= 3 present).")
        # Return concise summary for Pulumi outputs
        return f"deleted={len(deleted)} kept={len(keep)}"
    except subprocess.CalledProcessError:
        return "cleanup-skip"

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
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="DB_HOST", value=db_host),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="DB_PORT", value=db_port),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="DB_NAME", value=db_name),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="DB_USERNAME", value=db_username),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="DB_PASSWORD", value=db_password),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="DB_SSL_PARAMS", value=db_ssl_params),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name="SUPABASE_CA_CERT", value=supabase_ca_content),
                ],
                resources=gcp.cloudrunv2.ServiceTemplateContainerResourcesArgs(
                    limits={
                        "memory": "512Mi", # 512Mi is the minimum required for Cloud Run v2 with CPU allocation.
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

# Run image cleanup after a successful deployment (depends on service URI materializing)
cleanup_result = run_service.uri.apply(cleanup_old_images_after_deploy)
pulumi.export("image_cleanup", cleanup_result)
