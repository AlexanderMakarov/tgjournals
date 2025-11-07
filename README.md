# Telegram Bot for Journals

This is a Telegram bot for managing soccer players' journals.

Idea: admin (coach) makes list of questions before traning session and after it - both parts make a journal per session (day). Questions require free form answer (text).
Bot allows players to participate in this quiz and saves answers to the journal.

Journals are available to read for players themselves and admin (coach) could read them for all players.

## Specs

Project was created mostly to refresh Java, Spring Boot, skills. Also it is a way to learn GraalVM native image compilation, newer Java versions, Pulumi, etc.

Main technologies:
- Java 21,
- Spring Boot 3.5.6,
- PostgreSQL 18,
- GraalVM native image compilation
- Pulumi
- Google Cloud Run v2

## Features

- 2 roles: admin (aka coach) and player.
- Only one set of questions for the session could be active - it is avaialable to take by players any time.
- Admin writes questions in the format - one question per line, if line starts with "Before: " then this is a question before (and this prefix is not kept in the question), if starts with "After: " then this is question after the training session. Everything should fit into one message. For example admin runs `/questions` command and after "Please update session questions" invite from the bot provides following message:
```
Before: What is your personal goal on this session?
After: Have goal been archived?
After: If yes then how?
After: If no then why?
After: What you did good during todays session?
After: What you would try to work on on next session?
```
- Players answer on questions one-by-one, "before or after" information only makes 2 groups of questions, "before" questions for "/before" command, "after" questions for "/after" command. Flow is - run "/before" before the session, answer on questions one-by-one, at the end get something like "Done for now, good luck with the sesssion, run `/after` command once you finish it." from the bot and follow this instruction after the session.
- Players may see theirs journals via `/last` command. Each journal starts with date label, next question, colon, answer. `/last5` command should return the last 5 journals. `/last50` command should return the last 50 journals.
- Admin may get list of players participating in journals with `/participants`.

# Setup

## 1. Create a Telegram Bot

1. Open Telegram and search for `@BotFather`
2. Send `/newbot` command
3. Follow the prompts to create your bot:
   - Choose a name for your bot (e.g., "Journals Bot")
   - Choose a username (must end with 'bot', e.g., "journals_soccer_bot")
4. Save the **Bot Token** provided by BotFather
5. Save the **Bot Username** (without @)
6. Create `.env` file by copying `.env.example` and filling in the values:
   ```bash
   cp .env.example .env
   # Edit .env with your bot credentials, webhook secret, and database credentials
   ```

## 2. PostgreSQL Setup

For development purposes project requires PostgreSQL to be installed and running.

In production project uses Supabase database.

### Install and Start PostgreSQL

**On Ubuntu/Debian:**
```bash
# Install PostgreSQL (if not already installed)
sudo apt update
sudo apt install postgresql postgresql-contrib

# Start PostgreSQL service
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Check if PostgreSQL is running
sudo systemctl status postgresql
```

**On macOS:**
```bash
# Install via Homebrew
brew install postgresql@18
brew services start postgresql@18
```

### Create Database

```bash
# Create a user and database for the application
sudo -u postgres psql

# In PostgreSQL prompt, run:
CREATE DATABASE journals;
CREATE DATABASE test_journals;  # For tests

# Or create user with password (optional):
CREATE USER journals_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE journals TO journals_user;
GRANT ALL PRIVILEGES ON DATABASE test_journals TO journals_user;

# Exit PostgreSQL prompt
\q
```

### Configure Database Connection

The application uses the following defaults (can be overridden via environment variables):
- **Host**: `localhost`
- **Port**: `5432`
- **Database**: `journals` (for tests: `test_journals`)
- **Username**: `postgres`
- **Password**: `postgres`

#### Local Development

For local development, set these in your `.env` file:
```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=journals
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

If you don't set these in `.env`, the application will use the defaults (localhost).

#### Production Deployment

For production deployment (Cloud Run), set these **separate** production database values in your `.env` file:
```bash
DB_PROD_HOST=your-production-db-host.example.com
DB_PROD_PORT=5432
DB_PROD_NAME=journals
DB_PROD_USERNAME=your-production-db-user
DB_PROD_PASSWORD='your-production-db-password'
DB_PROD_SSL_PARAMS='?sslmode=require'
```

**Important**: If your password contains special characters (like `$`, `&`, `)`, etc.), use **single quotes** around the value to prevent shell expansion. For example:
- ✅ Correct: `DB_PROD_PASSWORD='pass)@$&word'`
- ❌ Wrong: `DB_PROD_PASSWORD="pass)@$&word"` (double quotes allow `$` expansion)
- ❌ Wrong: `DB_PROD_PASSWORD=pass)@$&word` (no quotes will cause syntax issues)

**Note**: Python's Pulumi script uses `override=True` when loading the `.env` file, so it reads values directly from the file and ignores any Make-expanded environment variables.

**Note**: The Pulumi deployment script reads `DB_PROD_*` variables from your `.env` file and sets them as `DB_*` environment variables in Cloud Run.

You can keep both configurations in the same `.env` file - local values for development, production values for deployment.

**Note**: Tests automatically create the `test_journals` database if it doesn't exist. Just run `make test` and the database setup will be handled automatically.

#### Testing Production Database Connection Locally

Before deploying to Cloud Run, you can test the production database connection in two ways:

**1. Test database connection only:**
```bash
# Test production database connection (uses DB_PROD_* variables from .env)
make db-test-prod
```
This will:
- Connect to your production database (Supabase)
- Test the connection
- Show database version and tables in the public schema

**2. Run the application with production database:**
```bash
# Run with JDK (Spring Boot JVM mode)
make run-prod

# Or run with native image (faster, closer to production)
make native-build   # Build native image first
make native-run-prod
```

This will:
- Start the application locally
- Connect to your production database (Supabase)
- Use the same configuration as Cloud Run
- Allow you to test the full application before deploying

Make sure your `.env` file has the `DB_PROD_*` variables set correctly before running these commands.

## 3. Local Development Setup

### Quick Start (Using Makefile)

The easiest way to get started is using the provided Makefile:

```bash
# 1. Set up environment
cp .env.example .env
# Edit .env with your bot credentials

# 2. Start the application
make run
# Run this in one terminal

# 3. Set up webhook automatically (in another terminal)
make set-local
# This will start ngrok and set webhook automatically

# 4. Check everything is working
make health
make check-webhook
```

### Manual Setup

1. Install ngrok: https://ngrok.com/download
2. Start your application:
   ```bash
   make run
   # or: ./gradlew bootRun
   ```
3. In another terminal, expose your local server:
   ```bash
   make ngrok
   # or: ngrok http 8080
   ```
4. Copy the HTTPS URL from ngrok (e.g., `https://abc123.ngrok.io`)
5. Set the webhook URL:
   ```bash
   NGROK_URL=https://abc123.ngrok.io make set-webhook
   ```

## 4. Production Deployment

For production, deploy your application to Google Cloud Run v2 and set the webhook on Telegram server side to avoid abusing by third parties.

### Webhook Security

To set webhook on Telegram server side, you need to generate a secret token and add it to your `.env` file.

#### How to generate a secret token

1. **Generate a strong secret token**:
   ```bash
   # Generate a random secret (32 characters)
   openssl rand -hex 16
   ```

2. **Add to your `.env` file**:
   ```bash
   TELEGRAM_WEBHOOK_SECRET=your_generated_secret_here
   ```

3. **The application will automatically**:
   - Validate the secret token on each webhook request
   - Reject unauthorized requests with 401 status
   - Log security violations for monitoring

**Without a secret token**: The webhook will be open to everyone (development mode only).

#### How to set webhook on Telegram server side

```bash
# Set production webhook (will ask for your domain URL)
make set-prod
```

### Deployment to GCP Cloud Run v2

This project uses Pulumi to deploy to Google Cloud Run v2 with GraalVM native compilation for fast startup times (~0.4 seconds).

#### Prerequisites

1. **Pulumi CLI**: Install with one command
   ```bash
   curl -fsSL https://get.pulumi.com | sh
   # Restart your shell or run: export PATH=$PATH:$HOME/.pulumi/bin
   ```

2. **Python 3.8+**: For Pulumi dependencies
   ```bash
   cd pulumi
   python3 -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   pip install -r requirements.txt
   ```

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

#### GCP Project Setup

1. **Create or select a GCP project**:
   ```bash
   gcloud projects create <project-id>
   gcloud config set project <project-id>
   ```

2. **Enable necessary APIs**:
   ```bash
   gcloud services enable \
      artifactregistry.googleapis.com \
      run.googleapis.com \
      iam.googleapis.com \
      logging.googleapis.com \
      monitoring.googleapis.com \
      cloudbuild.googleapis.com \
      compute.googleapis.com
   ```
   **Note**: Compute Engine API is required for Pulumi to manage Cloud Run resources properly.

3. **Configure Docker to push images to Artifact Registry**:
   ```bash
   # Replace <region> with your GCP region (e.g., europe-west1)
   gcloud auth configure-docker <region>-docker.pkg.dev
   ```
   Note: Artifact Registry is the recommended container registry for GCP. The old Google Container Registry (GCR) is deprecated.

4. **Ensure your database is accessible**:
   - For **production deployment**, set `DB_PROD_HOST`, `DB_PROD_PORT`, `DB_PROD_NAME`, `DB_PROD_USERNAME`, and `DB_PROD_PASSWORD` in your `.env` file pointing to your production database (e.g., Supabase)
   - These values will be read by Pulumi and set as `DB_*` environment variables in Cloud Run
   - For **local development**, use `DB_HOST`, `DB_PORT`, etc. with localhost values
   - Cloud Run automatically sets `PORT=8080` - your application must listen on this port (default configuration does this)

5. **Set up your `.env` file** in the project root with required variables:
   - `GCP_PROJECT_ID`: Your Google Cloud project ID
   - `GCP_REGION`: Google Cloud region (default: europe-west1)
   - `FUNCTION_NAME`: Cloud Run service name (default: tg-journals-function)
   - `TELEGRAM_BOT_TOKEN`: Your Telegram bot token
   - `TELEGRAM_WEBHOOK_SECRET`: Webhook secret for security
   - **For local development**: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` (use localhost)
   - **For production**: `DB_PROD_HOST`, `DB_PROD_PORT`, `DB_PROD_NAME`, `DB_PROD_USERNAME`, `DB_PROD_PASSWORD` (use your production database)

#### Deployment Steps

1. **Build Docker image locally** (GraalVM native compilation takes time):
   ```bash
   # From project root
   make docker-build
   ```

2. **Initialize Pulumi using local filesystem state** (first time only):
   ```bash
   cd pulumi
   source venv/bin/activate  # Activate venv if not already activated
   mkdir -p ../.pulumi-state
   pulumi login file://$(pwd)/../.pulumi-state
   pulumi stack init dev
   ```

3. **Deploy the infrastructure** (uses your local Docker image):
   ```bash
   # From project root
   make gcp-deploy
   # Or from pulumi directory (with venv activated):
   # cd pulumi
   # source venv/bin/activate
   # pulumi up
   ```

4. **View outputs**:
   ```bash
   cd pulumi
   source venv/bin/activate  # Activate venv if not already activated
   pulumi stack output
   ```

#### What Gets Deployed

- **Artifact Registry Repository**: Docker repository for storing container images
- **Docker Image**: Pushes your locally built image to Artifact Registry
- **Cloud Run v2 Service**: Serverless container service with minimal resource usage
- **Service Account**: With minimal required permissions (logging, monitoring)
- **IAM Bindings**: For public access to the Cloud Run service
- **HTTPS**: Provided by default (no additional cost)

#### Resource Specifications

- **Memory**: 512Mi (minimum required for Cloud Run v2 with CPU allocation)
- **CPU**: 1 vCPU
- **Scaling**: Min 0 instances, Max 1 instance (scales to zero when idle)
- **Request Timeout**: 60 seconds
- **Startup Time**: ~0.4 seconds (GraalVM native image)

#### Cleanup

To destroy all resources (including Artifact Registry repository and images):
```bash
cd pulumi
source venv/bin/activate  # Activate venv if not already activated
mkdir -p ../.pulumi-state
pulumi login file://$(pwd)/../.pulumi-state
pulumi destroy
```

This will remove:
- Cloud Run v2 service
- Artifact Registry repository and all Docker images
- Service account and IAM bindings

**Note**: All infrastructure is managed by Pulumi, so `pulumi destroy` will clean up everything.

## 5. Webhook Management

**Set Local Webhook** (for development with ngrok):
```bash
make set-local
```

**Set Production Webhook** (for production deployment):
```bash
make set-prod
```

**Check Webhook Status**:
```bash
make check-webhook
```

**Delete Webhook** (to stop receiving updates):
```bash
make delete-webhook
```

## 6. Verify Setup

1. Start the application: `make run`
2. Check the health endpoint: `make health`
3. Set the webhook URL: `make set-local` (for development) or `make set-prod` (for production)
4. Send `/start` to your bot in Telegram
5. Check webhook status: `make check-webhook`

## 7. Available Endpoints

- **Health Check**: `GET /health` - Returns database statistics with caching
- **API Documentation**: `GET /docs` - Swagger UI
- **Webhook**: `POST /webhook` - Telegram webhook endpoint

## 8. DB Migrations

Liquibase or Flyway are too heavy/long solutions (they check schema on startup) so keep db migrations manual for now.
When schema changes are introduced, a standalone SQL file is added to the [`manual_migrations`](manual_migrations) directory with the date in "YYYY_MM_DD.sql" format.

How to apply a migration:

```bash
# Example for the existing migration.
psql "$DB_NAME" -h "$DB_HOST" -U "$DB_USERNAME" -p "$DB_PORT" -f manual_migrations/2025_11_04.sql
```

### Health and Monitoring
```bash
make health            # Check local application health
```

# Roadmap/TODO

- [x] Add support for localization + Russian.
- [ ] Fix removing old images from GCP Artifact Registry.
- [ ] Add multiple teams.
- [ ] Send notifications to players about new sessions (to avoid spam players can just delete bot - journals will stay anyway).
- [ ] Auto-remove old journals or extend ability to view them.
- [ ] Search in team journals by text (admin only).
