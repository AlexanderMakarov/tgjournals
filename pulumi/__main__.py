import pulumi
import pulumi_gcp as gcp
import os
import subprocess
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv("../.env")

# Get configuration
config = pulumi.Config()
project_id = os.getenv("GCP_PROJECT_ID")
region = os.getenv("GCP_REGION", "europe-west1")  # Belgium - cheapest European region
function_name = os.getenv("FUNCTION_NAME", "tg-journals-function")

# Validate required environment variables
if not project_id:
    raise ValueError("GCP_PROJECT_ID must be set in .env file")

# Docker image configuration
image_name = f"gcr.io/{project_id}/tg-journals"
image_tag = "latest"

# Create a service account for the Cloud Function
service_account = gcp.serviceaccount.Account(
    "tg-journals-sa",
    account_id="tg-journals-sa",
    display_name="Telegram Journals Bot Service Account",
    description="Service account for Telegram Journals Bot Cloud Function"
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

# Create a persistent disk for database storage (minimal size for cost optimization)
persistent_disk = gcp.compute.Disk(
    "tg-journals-disk",
    name="tg-journals-disk",
    type="pd-standard",
    size=1,  # 1 GB - minimal size for SQLite database
    zone=f"{region}-a",
    description="Persistent disk for Telegram Journals Bot database"
)

# Use locally built Docker image (assumes you've already built it)
def push_local_image():
    """Push locally built Docker image to Google Container Registry"""
    try:
        # Check if local image exists
        local_image = "tg-journals:latest"
        check_cmd = ["docker", "images", "-q", local_image]
        result = subprocess.run(check_cmd, capture_output=True, text=True)
        
        if not result.stdout.strip():
            raise Exception(f"Local Docker image '{local_image}' not found. Please build it first with: make docker-build")
        
        # Tag for GCR
        tag_cmd = ["docker", "tag", local_image, f"{image_name}:{image_tag}"]
        subprocess.run(tag_cmd, check=True)
        
        # Configure Docker for GCR
        auth_cmd = ["gcloud", "auth", "configure-docker"]
        subprocess.run(auth_cmd, check=True)
        
        # Push to GCR
        push_cmd = ["docker", "push", f"{image_name}:{image_tag}"]
        subprocess.run(push_cmd, check=True)
        
        return f"{image_name}:{image_tag}"
    except subprocess.CalledProcessError as e:
        raise Exception(f"Failed to push local Docker image: {e}")

# Push the locally built Docker image
docker_image = push_local_image()

# Note: Cloud Functions provide HTTPS by default without additional cost.

# Create a Cloud Function with persistent disk
cloud_function = gcp.cloudfunctionsv2.Function(
    "tg-journals-function",
    name=function_name,
    location=region,
    description="Telegram Journals Bot Cloud Function",
    
    build_config=gcp.cloudfunctionsv2.FunctionBuildConfigArgs(
        runtime="java21",
        entry_point="com.aleksandrmakarov.journals.JournalsApplication",
        source=gcp.cloudfunctionsv2.FunctionBuildConfigSourceArgs(
            storage_source=gcp.cloudfunctionsv2.FunctionBuildConfigSourceStorageSourceArgs(
                bucket=os.getenv("GCP_BUCKET_NAME", f"{project_id}-tg-journals-source"),
                object="source.zip"
            )
        ),
        docker_repository=f"gcr.io/{project_id}/tg-journals"
    ),
    
    service_config=gcp.cloudfunctionsv2.FunctionServiceConfigArgs(
        max_instance_count=10,
        min_instance_count=0,
        available_memory="1Gi",
        timeout_seconds=300,
        service_account_email=service_account.email,
        environment_variables={
            "TELEGRAM_BOT_TOKEN": os.getenv("TELEGRAM_BOT_TOKEN", ""),
            "TELEGRAM_WEBHOOK_SECRET": os.getenv("TELEGRAM_WEBHOOK_SECRET", ""),
            "SPRING_PROFILES_ACTIVE": "production",
            "DATABASE_PATH": "/mnt/disk/journals.db"
        },
        volumes=[
            gcp.cloudfunctionsv2.FunctionServiceConfigVolumeArgs(
                name="disk",
                source="disk"
            )
        ],
        volume_mounts=[
            gcp.cloudfunctionsv2.FunctionServiceConfigVolumeMountArgs(
                name="disk",
                mount_path="/mnt/disk"
            )
        ]
    )
)

# Create a Cloud Function IAM binding for public access
cloud_function_iam = gcp.cloudfunctionsv2.FunctionIamBinding(
    "tg-journals-function-iam",
    project=cloud_function.project,
    location=cloud_function.location,
    name=cloud_function.name,
    role="roles/cloudfunctions.invoker",
    members=["allUsers"]
)

# Load balancer and SSL certificate removed for cost optimization
# Cloud Functions provide HTTPS endpoints by default

# Export the function URL and basic info
pulumi.export("function_url", cloud_function.url)
pulumi.export("function_name", cloud_function.name)
pulumi.export("service_account_email", service_account.email)
pulumi.export("persistent_disk_name", persistent_disk.name)
pulumi.export("region", region)
pulumi.export("cost_optimized", "✅ Minimal disk (1GB), no load balancer, Europe region")
