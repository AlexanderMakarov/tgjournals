# Google Cloud Functions Deployment Guide

This guide will help you deploy your Telegram Journals Bot to Google Cloud Functions using GraalVM native compilation for optimal performance.

## Prerequisites

1. **Google Cloud SDK**: Install and configure
   ```bash
   # Install gcloud CLI
   curl https://sdk.cloud.google.com | bash
   exec -l $SHELL
   
   # Authenticate
   gcloud auth login
   gcloud auth application-default login
   ```

2. **Docker**: Install Docker for building container images
   ```bash
   # Ubuntu/Debian
   sudo apt update
   sudo apt install docker.io
   sudo usermod -aG docker $USER
   ```

3. **Pulumi**: Install Pulumi CLI
   ```bash
   curl -fsSL https://get.pulumi.com | sh
   ```

4. **Python**: For Pulumi dependencies
   ```bash
   sudo apt install python3 python3-pip python3-venv
   ```

## Setup

1. **Copy environment template**:
   ```bash
   cp env.template .env
   ```

2. **Edit `.env` file** with your values:
   ```bash
   nano .env
   ```
   
   Required values:
   - `TELEGRAM_BOT_TOKEN`: Your bot token from @BotFather
   - `TELEGRAM_WEBHOOK_SECRET`: A secure random string
   - `GCP_PROJECT_ID`: Your Google Cloud project ID

3. **Set up Pulumi environment**:
   ```bash
   cd pulumi
   python3 -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
   pulumi stack init dev
   ```

## Deployment Steps

### Option 1: Full Automated Deployment
```bash
# Complete deployment pipeline
make full-deploy
```

### Option 2: Step-by-Step Deployment

1. **Build native image**:
   ```bash
   make native-build
   ```

2. **Build Docker image**:
   ```bash
   make docker-build
   ```

3. **Test locally** (optional):
   ```bash
   make docker-run
   ```

4. **Push to Google Container Registry**:
   ```bash
   make docker-push
   ```

5. **Deploy to Google Cloud Functions**:
   ```bash
   make gcp-deploy
   ```

## Performance Optimizations

### GraalVM Native Image Benefits
- **Startup time**: < 2.5 seconds (vs 40+ seconds with standard JVM)
- **Memory usage**: ~50MB (vs 200MB+ with standard JVM)
- **Image size**: ~50MB (vs 300MB+ with standard JVM)

### Google Cloud Functions Optimizations
- **CPU Boost**: Enabled for faster cold starts
- **Persistent Disk**: For database storage
- **Memory**: 1GB allocated
- **Timeout**: 5 minutes

## Monitoring and Logs

1. **View logs**:
   ```bash
   make gcp-logs
   ```

2. **Check function status**:
   ```bash
   gcloud functions describe tg-journals-function --region=us-central1
   ```

3. **Monitor metrics**:
   - Go to Google Cloud Console > Cloud Functions
   - View metrics, logs, and performance data

## Troubleshooting

### Common Issues

1. **Build failures**:
   ```bash
   # Clean and rebuild
   make clean
   make native-build
   ```

2. **Docker build issues**:
   ```bash
   # Check Docker is running
   docker --version
   sudo systemctl start docker
   ```

3. **Authentication issues**:
   ```bash
   gcloud auth login
   gcloud auth application-default login
   ```

4. **Permission issues**:
   ```bash
   # Ensure your account has necessary roles
   gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
     --member="user:YOUR_EMAIL" \
     --role="roles/cloudfunctions.admin"
   ```

### Performance Issues

1. **Slow cold starts**: Ensure GraalVM native compilation is working
2. **Memory issues**: Check logs for out-of-memory errors
3. **Database issues**: Verify persistent disk is mounted correctly

## Cleanup

To remove all resources:
```bash
make gcp-delete
```

## Cost Optimization

- **Min instances**: 0 (pay only when used)
- **Max instances**: 10 (adjust based on traffic)
- **Memory**: 1GB (adjust based on usage)
- **Persistent disk**: 10GB (adjust based on data size)

## Security

- **Webhook secret**: Always use a strong secret token
- **Service account**: Minimal permissions
- **HTTPS only**: All communications encrypted
- **Environment variables**: Sensitive data in secure storage

## Next Steps

1. Set up monitoring and alerting
2. Configure custom domain (optional)
3. Set up CI/CD pipeline
4. Implement backup strategies for persistent data
