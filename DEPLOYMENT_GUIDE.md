# Google Cloud Functions Deployment Guide

This guide will help you deploy your Telegram Journals Bot to Google Cloud Functions using GraalVM native compilation for optimal performance and minimal resource usage.

## Overview

**Optimized Docker Image:**
- **Size**: 287MB (down from 1.4GB - 80% reduction)
- **Startup Time**: 0.376 seconds (vs 5-10s for JVM)
- **Memory Usage**: ~50MB runtime (vs 200-500MB for JVM)
- **Base Image**: debian:bookworm-slim with full SQLite support
- **Volume Support**: Persistent disk mounting at `/mnt/disk`

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
   
   **Without persistent volume:**
   ```bash
   make docker-run
   ```
   
   **With persistent volume (recommended):**
   ```bash
   # Create a volume for database storage
   docker volume create tg-journals-data
   
   # Run with volume mounted
   docker run -p 8080:8080 \
     -v tg-journals-data:/mnt/disk \
     -e DATABASE_PATH=/mnt/disk/journals.db \
     --env-file .env \
     tg-journals:latest
   ```
   
   **Verify it's working:**
   ```bash
   # Check health endpoint
   curl http://localhost:8080/health
   
   # Should return:
   # {"status":"UP","sessions":0,"users":0,"journals":0,...}
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
- **Startup time**: **0.376 seconds** (vs 5-10 seconds with standard JVM)
- **Memory usage**: **~50MB** (vs 200-500MB with standard JVM)
- **Image size**: **287MB** (vs 1.4GB unoptimized)
- **Cold start**: Near-instant (critical for Cloud Functions)

### Docker Image Optimizations
- **Base image**: debian:bookworm-slim (~80MB base)
- **Native executable**: GraalVM compiled binary
- **Minimal dependencies**: Only ca-certificates and libz1
- **SQLite support**: Full JNI compatibility
- **Volume mounting**: Persistent storage at `/mnt/disk`

### Google Cloud Functions Configuration
- **Memory**: 1GB allocated (can be adjusted based on usage)
- **Min instances**: 0 (cost-effective, pay only when used)
- **Max instances**: 10 (adjustable based on traffic)
- **Timeout**: 300 seconds (5 minutes)
- **Persistent disk**: 1GB SSD for SQLite database
- **Environment**:
  - `DATABASE_PATH=/mnt/disk/journals.db` (auto-configured by Pulumi)
  - Telegram bot credentials from `.env` file

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

1. **Slow cold starts**: 
   - Verify native image is being built (look for `tg-journals` binary)
   - Check startup time in logs (should be <0.5 seconds)
   - Ensure `STATIC_BUILD=true` is set during build

2. **Memory issues**: 
   - Check Cloud Functions logs for OOM errors
   - Consider increasing memory allocation in `pulumi/__main__.py`
   - Native image should use ~50MB; if higher, investigate

3. **Database issues**: 
   - Verify `DATABASE_PATH=/mnt/disk/journals.db` is set
   - Check persistent disk is mounted at `/mnt/disk`
   - Ensure nonroot user has write permissions
   - Look for SQLite errors in logs

4. **Container fails to start**:
   ```bash
   # Test locally first
   docker run -it --rm tg-journals:latest /bin/bash
   
   # If bash not available (expected), check logs
   docker logs CONTAINER_ID
   ```

5. **SQLite native library errors**:
   - Should not occur with debian:bookworm-slim base
   - If you see "libz.so.1 not found", rebuild image
   - Verify base image is `debian:bookworm-slim` not Alpine/distroless

## Cleanup

To remove all resources:
```bash
make gcp-delete
```

## Cost Optimization

Our configuration is optimized for minimal cost while maintaining performance:

- **Min instances**: 0 (pay only when function is invoked)
- **Max instances**: 10 (prevents runaway costs, adjust based on traffic)
- **Memory**: 1GB (native image uses ~50MB, 1GB provides headroom)
- **Persistent disk**: 1GB SSD (minimal size for SQLite database)
- **Region**: Configure in `.env` (europe-west1 is cost-effective for EU)
- **Cold starts**: <0.4s (fast enough to keep min instances at 0)

**Estimated Monthly Cost** (with light usage):
- Cloud Functions: $0-5 (first 2M invocations free)
- Persistent Disk: ~$0.17/month (1GB SSD)
- **Total**: ~$0.17-5/month

**Cost Reduction Achieved**:
- Image size: 80% smaller → Faster deployments, less storage
- Memory usage: 75% lower → Can use smaller instance size
- Startup time: 95% faster → Can use min instances = 0

## Security

Our deployment follows security best practices:

- **Non-root user**: Container runs as UID 65532 (nonroot)
- **Minimal image**: Only essential dependencies (ca-certificates, libz1)
- **Webhook secret**: Strong secret token for webhook verification
- **Service account**: Minimal IAM permissions (logging + monitoring only)
- **HTTPS only**: All communications encrypted via Google Cloud
- **Environment variables**: Sensitive data in `.env` (not in code)
- **No shell access**: Distroless-style security posture

## Image Optimization Details

For detailed information about how we achieved 80% size reduction, see:
- `image building docs/OPTIMIZATION_SUMMARY.md` - Complete optimization story
- `image building docs/DOCKER_BUILD_OPTIMIZATION.md` - Technical details

**Key achievements:**
1. **Native compilation**: GraalVM AOT compilation (no JVM at runtime)
2. **Debian Slim base**: Minimal ~80MB base with full glibc compatibility
3. **SQLite JNI support**: Works perfectly with native libraries
4. **Volume mounting**: Persistent storage at `/mnt/disk` for database
5. **Fast startup**: 0.376 seconds vs 5-10 seconds for JVM

## Next Steps

1. **Set up Telegram webhook**:
   ```bash
   # After deployment, get the function URL from output
   make check-webhook  # Verify current webhook
   make set-prod       # Set webhook to production URL
   ```

2. **Configure monitoring**:
   - Enable Cloud Monitoring alerts
   - Set up uptime checks
   - Configure log-based metrics

3. **Test the bot**:
   - Send `/start` command to your bot on Telegram
   - Verify database persistence across restarts
   - Test journal creation and retrieval

4. **Optional enhancements**:
   - Configure custom domain
   - Set up CI/CD pipeline (GitHub Actions)
   - Implement database backup strategy
   - Add custom metrics and dashboards

## Additional Resources

- **Docker optimization**: See `image building docs/` directory
- **GraalVM native image**: [Official Documentation](https://www.graalvm.org/latest/reference-manual/native-image/)
- **Spring Boot native**: [Spring Boot Documentation](https://docs.spring.io/spring-boot/reference/packaging/native-image/)
- **Google Cloud Functions**: [Official Documentation](https://cloud.google.com/functions/docs)
