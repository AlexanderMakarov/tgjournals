# Multi-stage build for GraalVM native image optimized for Google Cloud Functions
FROM container-registry.oracle.com/graalvm/native-image-community:25 AS builder

# Set working directory
WORKDIR /app

# Install build tools
RUN microdnf install -y findutils curl unzip

# Install Gradle 9.1.0+ which supports Java 25
RUN curl -L https://services.gradle.org/distributions/gradle-9.1.0-bin.zip -o gradle.zip && \
    unzip gradle.zip && \
    ln -s /app/gradle-9.1.0/bin/gradle /usr/local/bin/gradle && \
    rm gradle.zip

# Copy build files
COPY build.gradle .
COPY settings.gradle .

# Copy source code
COPY src src

# Build native image using Gradle with Java 25
ENV STATIC_BUILD=true
RUN gradle nativeCompile --no-daemon -PjavaVersion=25

# Final stage - minimal Debian slim image with all necessary libraries (~80MB base)
FROM debian:bookworm-slim

# Install runtime dependencies and create non-root user
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        libz1 \
        && \
    rm -rf /var/lib/apt/lists/* && \
    useradd -u 65532 -m -s /bin/bash nonroot

# Create directories with proper permissions
RUN mkdir -p /app /mnt/disk && \
    chown -R nonroot:nonroot /app /mnt/disk

# Copy the native executable
COPY --from=builder /app/build/native/nativeCompile/tg-journals /app/tg-journals

# Switch to non-root user
USER nonroot

# Set working directory to /app
WORKDIR /app

# Set environment variables for production
ENV SPRING_PROFILES_ACTIVE=production
ENV PORT=8080
ENV FUNCTION_TARGET=com.aleksandrmakarov.journals.JournalsApplication
ENV DATABASE_PATH=/mnt/disk/journals.db

# Set default Telegram bot configuration (should be overridden with actual values)
ENV TELEGRAM_BOT_TOKEN=""
ENV TELEGRAM_BOT_USERNAME=""
ENV TELEGRAM_WEBHOOK_PATH="/webhook"
ENV TELEGRAM_WEBHOOK_SECRET=""

# Expose the port
EXPOSE 8080

# Run the native executable
ENTRYPOINT ["/app/tg-journals"]
