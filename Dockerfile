# Multi-stage build for GraalVM native image optimized for Google Cloud Functions
FROM container-registry.oracle.com/graalvm/native-image-community:25 AS builder

# Set working directory
WORKDIR /app

# Install build tools
RUN microdnf install -y findutils curl unzip

# Install Gradle 9.1.0+ which supports Java 25
RUN curl -L https://services.gradle.org/distributions/gradle-9.1.0-bin.zip -o gradle.zip
RUN unzip gradle.zip
RUN ln -s /app/gradle-9.1.0/bin/gradle /usr/local/bin/gradle
RUN rm gradle.zip

# Copy build files
COPY build.gradle .
COPY settings.gradle .

# Copy source code
COPY src src

# Build native image using Gradle with Java 25
RUN gradle nativeCompile --no-daemon -PjavaVersion=25

# Final stage with native executable for Google Cloud Functions
FROM container-registry.oracle.com/graalvm/native-image-community:25

# Install minimal runtime dependencies
RUN microdnf install -y ca-certificates tzdata curl

# Create app directory
WORKDIR /app

# Copy the native executable
COPY --from=builder /app/build/native/nativeCompile/tg-journals .

# Set environment variables for production
ENV SPRING_PROFILES_ACTIVE=production
ENV PORT=8080
ENV FUNCTION_TARGET=com.aleksandrmakarov.journals.JournalsApplication

# Set default Telegram bot configuration (should be overridden with actual values)
ENV TELEGRAM_BOT_TOKEN=""
ENV TELEGRAM_BOT_USERNAME=""
ENV TELEGRAM_WEBHOOK_PATH="/webhook"
ENV TELEGRAM_WEBHOOK_SECRET=""

# Expose the port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run the native executable
ENTRYPOINT ["./tg-journals"]
