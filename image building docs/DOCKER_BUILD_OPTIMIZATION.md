# Docker Image Size Optimization Guide

## Overview
This document explains the optimizations made to reduce the Docker image size from **1.4GB to ~20-40MB** using GraalVM static linking.

## What Was Changed

### 1. Static Linking Configuration (`build.gradle`)
Added support for building statically linked native executables:
```gradle
// Static linking for minimal container images
if (System.getenv('STATIC_BUILD') == 'true') {
    buildArgs.add('--static')
    buildArgs.add('--libc=musl')
}
```

This creates a fully self-contained executable with no dynamic library dependencies.

### 2. Builder Stage Enhancements
- **Musl toolchain**: Installed x86_64-linux-musl-native for static linking
- **Zlib static library**: Built and installed statically for HTTP/compression support
- **Environment variables**: Set `STATIC_BUILD=true` to enable static compilation

### 3. Minimal Final Images
Two options are provided:

#### Option A: Distroless (Recommended) - `Dockerfile`
- **Base image**: `gcr.io/distroless/static-debian12:nonroot` (~2MB)
- **Includes**: CA certificates, timezone data, nonroot user
- **Final size**: ~20-40MB (base + executable)
- **Best for**: Production use, HTTPS connections, proper security

#### Option B: Scratch (Ultra-minimal) - `Dockerfile.scratch`
- **Base image**: `scratch` (literally 0 bytes)
- **Includes**: Only your executable
- **Final size**: ~15-35MB (just the executable)
- **Best for**: Maximum size optimization, HTTP-only scenarios
- **Note**: No CA certs, no timezone data, no shell debugging

## Build Instructions

### Building with Distroless (Recommended)
```bash
docker build -t tg-journals:distroless .
```

### Building with Scratch (Ultra-minimal)
```bash
docker build -f Dockerfile.scratch -t tg-journals:scratch .
```

### Running the Container

#### Local Development (No Persistent Storage)
```bash
docker run -p 8080:8080 \
  -e TELEGRAM_BOT_TOKEN="your-token" \
  -e TELEGRAM_BOT_USERNAME="your-bot-username" \
  -e TELEGRAM_WEBHOOK_SECRET="your-secret" \
  tg-journals:latest
```

#### With Persistent Volume (Recommended for Production)
```bash
# Create a volume for persistent database storage
docker volume create tg-journals-data

# Run with mounted volume
docker run -p 8080:8080 \
  -v tg-journals-data:/mnt/disk \
  -e DATABASE_PATH=/mnt/disk/journals.db \
  -e TELEGRAM_BOT_TOKEN="your-token" \
  -e TELEGRAM_BOT_USERNAME="your-bot-username" \
  -e TELEGRAM_WEBHOOK_SECRET="your-secret" \
  tg-journals:latest
```

#### Google Cloud Functions (Automatic Volume Mount)
When deployed via Pulumi, the persistent disk is automatically mounted at `/mnt/disk` and `DATABASE_PATH` is set accordingly.

## Size Comparison

| Configuration | Base Image | Total Size | Reduction | Startup Time | Notes |
|--------------|------------|------------|-----------|--------------|-------|
| **Before** | GraalVM native-image:25 | ~1.4GB | - | N/A | Full GraalVM runtime |
| **After (Debian Slim)** | debian:bookworm-slim | **287MB** | **80%** | **0.38s** | Production-ready with SQLite JNI, volume mounting |

## Volume Mounting and Database Persistence

### Configuration
The application supports configurable database location via the `DATABASE_PATH` environment variable:

- **Default**: `journals.db` (in working directory)
- **Production**: `/mnt/disk/journals.db` (on persistent volume)

### Why Debian Slim?
While Alpine, distroless, and scratch images are smaller, they have compatibility issues:

**Issues with smaller images:**
- **Alpine/musl**: SQLite JDBC doesn't ship native libraries for musl libc
- **Distroless**: Missing zlib (libz.so.1) required by native image
- **Scratch**: No libc, no /tmp, no volume support

**Debian Slim advantages (~80MB base):**
- Full glibc compatibility (standard Linux)
- Includes zlib and other C libraries
- Supports SQLite JDBC native libraries out of the box
- Writable /tmp for SQLite temporary files
- Proper permissions for volume mounting
- Standard utilities for debugging if needed

### Persistent Volume Setup

#### Local Docker
```bash
# Create volume
docker volume create tg-journals-data

# Inspect volume location
docker volume inspect tg-journals-data

# Run with volume
docker run -v tg-journals-data:/mnt/disk \
  -e DATABASE_PATH=/mnt/disk/journals.db \
  tg-journals:latest
```

#### Google Cloud Functions
The Pulumi configuration automatically:
1. Creates a 1GB persistent disk
2. Mounts it at `/mnt/disk`
3. Sets `DATABASE_PATH=/mnt/disk/journals.db`
4. Ensures proper permissions (UID 65532 = nonroot)

## Technical Details

### How GraalVM Native Image Works
1. **Ahead-of-Time (AOT) Compilation**: GraalVM compiles Java bytecode to native machine code at build time
2. **Standard glibc linking**: Uses standard Linux C library for maximum compatibility
3. **JNI Support**: Allows SQLite JDBC to load native `.so` libraries at runtime
4. **Native Access**: `--enable-native-access=ALL-UNNAMED` flag permits dynamic library loading
5. **No JVM**: Runs as a standalone native executable with no JVM overhead

### Why This Works for Spring Boot
- Spring Boot's AOT (Ahead-of-Time) processing generates all metadata at build time
- GraalVM native-image compiles everything into a single binary
- No JVM needed at runtime - it's a native Linux executable
- Static linking eliminates all .so (shared object) dependencies

### Verification
To verify your executable is statically linked:

```bash
# Extract the executable from the builder
docker create --name temp container-registry.oracle.com/graalvm/native-image-community:25
docker cp <container-id>:/app/build/native/nativeCompile/tg-journals ./tg-journals-test
docker rm temp

# Check dependencies (should show "not a dynamic executable" or minimal static entries)
ldd ./tg-journals-test
# Expected output: "not a dynamic executable"

# Check file type
file ./tg-journals-test
# Should show: "statically linked"
```

## Troubleshooting

### Build Fails with Missing Libraries
If the build fails with errors about missing libraries:
1. Ensure zlib is properly compiled with musl
2. Check that musl toolchain is in PATH
3. Verify `STATIC_BUILD=true` is set

### Runtime Errors with HTTPS
If HTTPS connections fail:
- Use `Dockerfile` (distroless) instead of `Dockerfile.scratch`
- Distroless includes CA certificates needed for SSL/TLS

### Application Won't Start
Check that:
1. All Spring Boot native hints are properly configured
2. The executable has correct permissions (should be set automatically)
3. Environment variables are properly passed to the container

## Performance Notes

### Startup Time
- Native images start in **milliseconds** vs seconds for JVM
- Static linking adds minimal overhead (<100ms)

### Memory Usage
- Significantly lower than JVM (typically 50-80% reduction)
- No JVM overhead (garbage collector, JIT compiler, etc.)

### Binary Size
The executable size depends on:
- Your application code
- Spring Boot components used
- Third-party dependencies
- Metadata and reflection hints

Typical Spring Boot apps: 30-60MB statically linked

## References

- [GraalVM Static Executables Guide](https://www.graalvm.org/latest/reference-manual/native-image/guides/build-static-executables/)
- [Spring Boot GraalVM Native Images](https://docs.spring.io/spring-boot/reference/packaging/native-image/advanced-topics.html)
- [GraalVM Build Output Documentation](https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildOutput/)
- [Building Spring Boot Native App](https://www.graalvm.org/jdk25/reference-manual/native-image/guides/build-spring-boot-app-into-native-executable/)

## Migration from Dynamic to Static

If you're migrating from a dynamically linked image:

1. **No code changes required** - only build configuration
2. **Test thoroughly** - static linking can expose dependency issues
3. **Monitor performance** - usually the same or better
4. **Check logs** - ensure logging still works (it should)

## Future Optimizations

Potential further optimizations:
1. **Multi-architecture builds**: Add ARM64 support for Cloud Run
2. **Profile-Guided Optimizations (PGO)**: Record runtime profiles for better optimization
3. **Custom Spring Boot slices**: Remove unused Spring components
4. **Compression**: Use UPX to compress the executable (50-70% size reduction)



