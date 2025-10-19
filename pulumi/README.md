# Pulumi Deployment for Telegram Journals Bot

This directory contains the Pulumi configuration for deploying the Telegram Journals Bot to Google Cloud Functions with automatic Docker image building, pushing, and TLS certificate management.

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

2. **Set up your `.env` file** in the project root with required variables (see .env.example)

3. **Initialize Pulumi** (first time only):
   ```bash
   pulumi stack init dev
   ```

## Deployment

1. **Build Docker image locally** (GraalVM native compilation takes time):
   ```bash
   # From project root
   make docker-build
   ```

2. **Deploy the infrastructure** (uses your local Docker image):
   ```bash
   pulumi up
   ```

3. **View outputs**:
   ```bash
   pulumi stack output
   ```

## What Pulumi Manages

- **Docker Image**: Pushes your locally built image to Google Container Registry
- **Google Cloud Function**: 2nd generation with persistent disk
- **Service Account**: With minimal required permissions
- **Persistent Disk**: 1GB for database storage (cost optimized)
- **IAM Bindings**: For public access
- **HTTPS**: Provided by default (no additional cost)

## Environment Variables Required

Make sure your `.env` file contains:
- `GCP_PROJECT_ID`: Your Google Cloud project ID
- `GCP_REGION`: Google Cloud region (default: us-central1)
- `FUNCTION_NAME`: Cloud Function name (default: tg-journals-function)
- `TELEGRAM_BOT_TOKEN`: Your Telegram bot token
- `TELEGRAM_WEBHOOK_SECRET`: Webhook secret for security

## Resources Created

- **Google Cloud Function** (2nd gen) with GraalVM native image
- **Docker Image** automatically built and pushed to GCR
- **SSL Certificate** (free managed certificate)
- **Global Load Balancer** with HTTPS proxy
- **Service Account** with minimal permissions
- **Persistent Disk** for database storage
- **IAM Bindings** for public access

## Cleanup

To destroy all resources:
```bash
pulumi destroy
```
