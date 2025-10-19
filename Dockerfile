# Multi-stage build for GraalVM native image optimized for Google Cloud Functions
FROM container-registry.oracle.com/graalvm/native-image-community:25 AS builder

# Set working directory
WORKDIR /app

# Install only xargs (needed by Gradle wrapper)
RUN microdnf install -y findutils

# Copy Gradle wrapper and build files first for better caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Copy source code
COPY src src

# Build native image
RUN chmod +x gradlew
RUN ./gradlew nativeCompile --no-daemon

# Final stage with native executable for Google Cloud Functions
FROM alpine:latest

# Install required packages for Google Cloud Functions
RUN apk --no-cache add \
    ca-certificates \
    tzdata \
    curl

# Create app directory
WORKDIR /app

# Copy the native executable
COPY --from=builder /app/build/native/nativeCompile/app .

# Create directory for persistent disk mount
RUN mkdir -p /mnt/disk

# Set environment variables for Google Cloud Functions
ENV PORT=8080
ENV FUNCTION_TARGET=com.aleksandrmakarov.journals.JournalsApplication

# Expose the port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run the native executable
ENTRYPOINT ["./app"]
