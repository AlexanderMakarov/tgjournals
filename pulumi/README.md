# Pulumi Deployment for Telegram Journals Bot

This directory contains the Pulumi configuration for deploying the Telegram Journals Bot to Google Cloud Run v2 with automatic Docker image building, pushing to Artifact Registry, and resource management.

## Prerequisites

1. **Pulumi CLI**: Install with one command
   ```bash
   curl -fsSL https://get.pulumi.com | sh
   # Restart your shell or run: export PATH=$PATH:$HOME/.pulumi/bin
   ```

2. **Python 3.8+**: Any version you have installed locally

3. **Google Cloud SDK**: Install and authenticate
   ```bash
   # Install gcloud CLI
   curl https://sdk.cloud.google.com | bash
   exec -l $SHELL
   
   # Authenticate
   gcloud auth login
   gcloud auth application-default login
   ```

4. **Docker**: For building and pushing images
   ```bash
   # Ubuntu/Debian
   sudo apt update
   sudo apt install docker.io
   sudo usermod -aG docker $USER
   ```

## Setup

1. **Install Python dependencies** (using your local Python):
   ```bash
   cd pulumi
   pip install -r requirements.txt
   ```

2. **Set up your GCP project** (see GCP_PROJECT_ID in .env.example)

Create a new project or use an existing one:
   ```bash
   gcloud projects create <project-id>
   gcloud config set project <project-id>
   ```
Enable necessary APIs:
   ```bash
   gcloud services enable \
      artifactregistry.googleapis.com \
      run.googleapis.com \
      iam.googleapis.com \
      logging.googleapis.com \
      monitoring.googleapis.com
   ```
Configure Docker to push images to Artifact Registry:
   ```bash
   # Replace <region> with your GCP region (e.g., europe-west1)
   gcloud auth configure-docker <region>-docker.pkg.dev
   ```
Note: Artifact Registry is the recommended container registry for GCP. The old Google Container Registry (GCR) is deprecated.

3. **Set up your `.env` file** in the project root with required variables (see .env.example)

4. **Initialize Pulumi using local filesystem state** (first time only):
   ```bash
   mkdir -p ../.pulumi-state
   pulumi login file://$(pwd)/../.pulumi-state
   pulumi stack init dev || true
   ```

## Deployment

1. **Build Docker image locally** (GraalVM native compilation takes time):
   ```bash
   # From project root
   make docker-build
   ```

2. **Deploy the infrastructure** (uses your local Docker image):
   ```bash
   mkdir -p ../.pulumi-state
   pulumi login file://$(pwd)/../.pulumi-state
   pulumi up
   ```

3. **View outputs**:
   ```bash
   pulumi stack output
   ```

## What Pulumi Manages

- **Artifact Registry Repository**: Docker repository for storing container images
- **Docker Image**: Pushes your locally built image to Artifact Registry
- **Cloud Run v2 Service**: Serverless container service with minimal resource usage
- **Service Account**: With minimal required permissions (logging, monitoring)
- **IAM Bindings**: For public access to the Cloud Run service
- **HTTPS**: Provided by default (no additional cost)

When you destroy the stack with `pulumi destroy`, all resources including the Artifact Registry repository and its images are removed.

## Environment Variables Required

Make sure your `.env` file contains:
- `GCP_PROJECT_ID`: Your Google Cloud project ID
- `GCP_REGION`: Google Cloud region (default: europe-west1)
- `FUNCTION_NAME`: Cloud Run service name (default: tg-journals-function)
- `TELEGRAM_BOT_TOKEN`: Your Telegram bot token
- `TELEGRAM_WEBHOOK_SECRET`: Webhook secret for security
- `SUPABASE_DATABASE_URL`: Your Supabase database connection URL (if using Supabase)

## Resources Created

- **Artifact Registry Repository** (`tg-journals`) for Docker images
- **Cloud Run v2 Service** with GraalVM native image
- **Docker Image** automatically built and pushed to Artifact Registry
- **Service Account** with minimal permissions (logging, monitoring)
- **IAM Bindings** for public access to Cloud Run service

### Resource Specifications

- **Memory**: 256Mi (optimized for GraalVM native images)
- **CPU**: 1 vCPU
- **Scaling**: Min 0 instances, Max 1 instance (scales to zero when idle)
- **Request Timeout**: 60 seconds
- **Startup Time**: ~0.4 seconds (GraalVM native image)

All resources are automatically cleaned up when you run `pulumi destroy`, including the Artifact Registry repository and all Docker images stored in it.

## Cleanup

To destroy all resources (including Artifact Registry repository and images):
```bash
mkdir -p ../.pulumi-state
pulumi login file://$(pwd)/../.pulumi-state
pulumi destroy
```

This will remove:
- Cloud Run v2 service
- Artifact Registry repository and all Docker images
- Service account and IAM bindings

**Note**: All infrastructure is managed by Pulumi, so `pulumi destroy` will clean up everything, including the container registry and stored images.
