# Makefile for Telegram Journals Bot

# Load environment variables from .env file
include .env
export

# Development tasks
.PHONY: run
run: ## Run the application (with local database)
	./gradlew bootRun

.PHONY: run-prod
run-prod: ## Run the application with production database (uses DB_PROD_* variables)
	@echo "Running application with production database..."
	@if [ -z "$(DB_PROD_HOST)" ]; then \
		echo "❌ Error: DB_PROD_HOST not set in .env file"; \
		exit 1; \
	fi; \
	PROD_HOST=$$DB_PROD_HOST; \
	PROD_PORT=$$DB_PROD_PORT; \
	PROD_NAME=$$DB_PROD_NAME; \
	PROD_PASS=$$(grep '^DB_PROD_PASSWORD=' .env | cut -d= -f2- | sed "s/^'\(.*\)'$$/\\1/" | sed 's/^"\(.*\)"$$/\1/'); \
	PROD_USER=$$DB_PROD_USERNAME; \
	PROD_USER=$$(echo "$$PROD_USER" | sed "s/^'\(.*\)'$$/\\1/" | sed 's/^"\(.*\)"$$/\1/'); \
	PROD_SSL=$$DB_PROD_SSL_PARAMS; \
	PROD_SSL=$$(echo "$$PROD_SSL" | sed "s/^'\(.*\)'$$/\\1/" | sed 's/^"\(.*\)"$$/\1/'); \
	# Strip surrounding quotes if present \
	PROD_HOST=$$(echo "$$PROD_HOST" | sed 's/^"\(.*\)"$$/\1/'); \
	PROD_PORT=$$(echo "$$PROD_PORT" | sed 's/^"\(.*\)"$$/\1/'); \
	PROD_NAME=$$(echo "$$PROD_NAME" | sed 's/^"\(.*\)"$$/\1/'); \
	if [ -z "$$PROD_HOST" ] || [ -z "$$PROD_PORT" ] || [ -z "$$PROD_NAME" ] || [ -z "$$PROD_USER" ] || [ -z "$$PROD_PASS" ]; then \
		echo "❌ Error: Missing required production database configuration"; \
		echo "Required: DB_PROD_HOST, DB_PROD_PORT, DB_PROD_NAME, DB_PROD_USERNAME, DB_PROD_PASSWORD"; \
		exit 1; \
	fi; \
	echo ""; \
	echo "Password length: $$(echo -n "$$PROD_PASS" | wc -c)"; \
	echo ""; \
	PROD_PASS_ESC=$$(echo "$$PROD_PASS" | sed "s/'/'\"'\"'/g"); \
	CMD="export DB_HOST=\"$$PROD_HOST\" && export DB_PORT=\"$$PROD_PORT\" && export DB_NAME=\"$$PROD_NAME\" && export DB_USERNAME=\"$$PROD_USER\" && export DB_PASSWORD='$$PROD_PASS_ESC' && export DB_SSL_PARAMS=\"$$PROD_SSL\" && export SPRING_PROFILES_ACTIVE=production && ./gradlew bootRun"; \
	echo "Command to execute:"; \
	echo "$$CMD" | sed "s/DB_PASSWORD='[^']*'/DB_PASSWORD='<hidden>'/"; \
	echo ""; \
	eval "$$CMD"

.PHONY: test
test: db-setup ## Run tests (ensures test database exists)
	./gradlew test

.PHONY: test-coverage
test-coverage: ## Run tests with coverage report
	./gradlew test jacocoTestReport printCoverage && xdg-open build/reports/jacoco/test/html/index.html >/dev/null 2>&1 || echo "Report: build/reports/jacoco/test/html/index.html"

.PHONY: format
format: ## Fix code formatting
	./gradlew spotlessApply

.PHONY: clean
clean: ## Clean build artifacts
	./gradlew clean

# ngrok and webhook setup
.PHONY: set-local
set-local: ## Start ngrok, set webhook automatically for local development
	@echo "Starting ngrok tunnel and setting webhook..."
	@echo "This will start ngrok in the background and automatically set the webhook."
	@echo "Press Ctrl+C to stop ngrok when done."
	@echo ""
	@ngrok http 8080 --log=stdout | while read line; do \
		if echo "$$line" | grep -q "started tunnel"; then \
			url=$$(echo "$$line" | grep -o 'https://[^[:space:]]*\.ngrok-free\.app'); \
			echo "Found ngrok URL: $$url"; \
			echo "Setting webhook to: $$url/webhook"; \
			if [ -n "$(TELEGRAM_WEBHOOK_SECRET)" ]; then \
				echo "Using webhook secret for security"; \
				curl -s -X POST "https://api.telegram.org/bot$(TELEGRAM_BOT_TOKEN)/setWebhook" \
					-H "Content-Type: application/json" \
					-d "{\"url\": \"$$url/webhook\", \"secret_token\": \"$(TELEGRAM_WEBHOOK_SECRET)\"}" > /dev/null; \
			else \
				echo "No webhook secret configured (development mode)"; \
				curl -s -X POST "https://api.telegram.org/bot$(TELEGRAM_BOT_TOKEN)/setWebhook" \
					-H "Content-Type: application/json" \
					-d "{\"url\": \"$$url/webhook\"}" > /dev/null; \
			fi; \
			echo "Webhook set successfully!"; \
		fi; \
	done

.PHONY: set-prod
set-prod: ## Set webhook for production (asks for URL)
	@echo "Setting webhook for production..."
	@read -p "Enter your production webhook URL (e.g., https://yourdomain.com): " url; \
	if [ -z "$$url" ]; then \
		echo "Error: URL is required"; \
		exit 1; \
	fi; \
	echo "Setting webhook to: $$url/webhook"; \
	if [ -z "$(TELEGRAM_WEBHOOK_SECRET)" ]; then \
		echo "ERROR: No webhook secret configured."; \
		exit 1; \
	fi; \
	curl -X POST "https://api.telegram.org/bot$(TELEGRAM_BOT_TOKEN)/setWebhook" \
		-H "Content-Type: application/json" \
		-d "{\"url\": \"$$url/webhook\", \"secret_token\": \"$(TELEGRAM_WEBHOOK_SECRET)\"}"; \
	echo "Webhook set successfully!"

.PHONY: check-webhook
check-webhook: ## Check current webhook status
	@echo "Checking webhook status..."
	@curl -s "https://api.telegram.org/bot$(TELEGRAM_BOT_TOKEN)/getWebhookInfo" | jq .

.PHONY: delete-webhook
delete-webhook: ## Delete webhook (stop receiving updates)
	@echo "Deleting webhook..."
	@curl -X POST "https://api.telegram.org/bot$(TELEGRAM_BOT_TOKEN)/deleteWebhook"
	@echo ""
	@echo "Webhook deleted successfully!"

# Database tasks
.PHONY: db-setup
db-setup: ## Set up PostgreSQL databases (creates databases if they don't exist)
	@echo "Setting up PostgreSQL databases..."
	@if ! systemctl is-active --quiet postgresql 2>/dev/null && ! pg_isready -q 2>/dev/null; then \
		echo "❌ PostgreSQL is not running. Starting service..."; \
		sudo systemctl start postgresql || (echo "Failed to start PostgreSQL. Please start it manually." && exit 1); \
	fi
	@echo "✅ PostgreSQL is running"
	@echo "Creating databases if they don't exist..."
	@PGPASSWORD=$(DB_PASSWORD) psql -h localhost -U $(DB_USERNAME) -d postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'journals'" 2>/dev/null | grep -q 1 || \
		PGPASSWORD=$(DB_PASSWORD) psql -h localhost -U $(DB_USERNAME) -d postgres -c "CREATE DATABASE journals;" 2>/dev/null || \
		(echo "❌ Failed to create 'journals' database. Check DB_USERNAME and DB_PASSWORD in .env file." && exit 1)
	@PGPASSWORD=$(DB_PASSWORD) psql -h localhost -U $(DB_USERNAME) -d postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'test_journals'" 2>/dev/null | grep -q 1 || \
		PGPASSWORD=$(DB_PASSWORD) psql -h localhost -U $(DB_USERNAME) -d postgres -c "CREATE DATABASE test_journals;" 2>/dev/null || \
		(echo "❌ Failed to create 'test_journals' database. Check DB_USERNAME and DB_PASSWORD in .env file." && exit 1)
	@echo "✅ Databases ready: journals, test_journals"

.PHONY: db-status
db-status: ## Check PostgreSQL connection and database status (local)
	@echo "Checking PostgreSQL status..."
	@if pg_isready -q; then \
		echo "✅ PostgreSQL is running"; \
	else \
		echo "❌ PostgreSQL is not running"; \
		exit 1; \
	fi
	@echo ""
	@echo "Database status:"
	@PGPASSWORD=$(DB_PASSWORD) psql -h localhost -U $(DB_USERNAME) -d postgres -tc "SELECT datname FROM pg_database WHERE datname IN ('journals', 'test_journals');" 2>/dev/null | grep -E "(journals|test_journals)" || echo "  No databases found"

.PHONY: db-test-prod
db-test-prod: ## Test connection to production database (uses DB_PROD_* variables only)
	@echo "Testing production database connection..."
	@if [ -z "$(DB_PROD_HOST)" ]; then \
		echo "❌ Error: DB_PROD_HOST not set in .env file"; \
		exit 1; \
	fi
	@PROD_HOST=$$DB_PROD_HOST; \
	PROD_PORT=$$DB_PROD_PORT; \
	PROD_NAME=$$DB_PROD_NAME; \
	PROD_USER=$$DB_PROD_USERNAME; \
	# Strip surrounding quotes if present \
	PROD_HOST=$$(echo "$$PROD_HOST" | sed 's/^"\(.*\)"$$/\1/'); \
	PROD_PORT=$$(echo "$$PROD_PORT" | sed 's/^"\(.*\)"$$/\1/'); \
	PROD_NAME=$$(echo "$$PROD_NAME" | sed 's/^"\(.*\)"$$/\1/'); \
	PROD_USER=$$(echo "$$PROD_USER" | sed 's/^"\(.*\)"$$/\1/' | sed "s/^'\(.*\)'$$/\\1/"); \
	# Read password directly from .env file to avoid $ expansion (use single quotes in .env) \
	PROD_PASS=$$(grep '^DB_PROD_PASSWORD=' .env | cut -d= -f2- | sed "s/^'\(.*\)'$$/\\1/" | sed 's/^"\(.*\)"$$/\1/'); \
	if [ -z "$$PROD_HOST" ] || [ -z "$$PROD_PORT" ] || [ -z "$$PROD_NAME" ] || [ -z "$$PROD_USER" ] || [ -z "$$PROD_PASS" ]; then \
		echo "❌ Error: Missing required production database configuration in .env file"; \
		echo "Required: DB_PROD_HOST, DB_PROD_PORT, DB_PROD_NAME, DB_PROD_USERNAME, DB_PROD_PASSWORD"; \
		exit 1; \
	fi; \
	# Clear any existing password files/environment \
	unset PGPASSFILE PGSSLMODE; \
	# Show the exact command being executed (without password) \
	echo ""; \
	echo "Executing: PGPASSWORD=<hidden> PGSSLMODE=require psql -h \"$$PROD_HOST\" -p \"$$PROD_PORT\" -d \"$$PROD_NAME\" -U \"$$PROD_USER\" -c \"SELECT version();\""; \
	echo ""; \
	# Pass password inline to avoid eval issues with special characters \
	if output=$$(PGSSLMODE=require PGPASSWORD=$$(printf '%s' "$$PROD_PASS") psql -h "$$PROD_HOST" -p "$$PROD_PORT" -d "$$PROD_NAME" -U "$$PROD_USER" -c "SELECT version();" 2>&1); then \
		echo "✅ Connection successful!"; \
		echo "Database version: $$output"; \
		echo "Tables in 'public' schema:"; \
		PGSSLMODE=require PGPASSWORD=$$(printf '%s' "$$PROD_PASS") psql -h "$$PROD_HOST" -p "$$PROD_PORT" -d "$$PROD_NAME" -U "$$PROD_USER" -c "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;" || true; \
	else \
		exit_code=$$?; \
		echo "❌ Connection failed (exit code: $$exit_code)!"; \
		echo ""; \
		echo "Debug information:"; \
		echo "  Host: $$PROD_HOST"; \
		echo "  Port: $$PROD_PORT"; \
		echo "  Database: $$PROD_NAME"; \
		echo "  Username: $$PROD_USER"; \
		echo "  Password length: $$(echo -n "$$PROD_PASS" | wc -c)"; \
		echo ""; \
		echo "Error output:"; \
		echo "$$output"; \
		echo ""; \
		echo "Note: Supabase requires SSL connections (PGSSLMODE=require is set)."; \
		if echo "$$output" | grep -q "password authentication failed"; then \
			echo "⚠️  Authentication failed - check your DB_PROD_USERNAME and DB_PROD_PASSWORD"; \
			echo "Note: For Supabase pooler, username format should be: postgres.PROJECT_REF"; \
		fi; \
		exit 1; \
	fi

.PHONY: db-reset
db-reset: ## Reset database (drop and recreate, WARNING: deletes all data!)
	@echo "⚠️  WARNING: This will delete all data in the 'journals' database!"
	@read -p "Are you sure? Type 'yes' to continue: " confirm; \
	if [ "$$confirm" != "yes" ]; then \
		echo "Cancelled."; \
		exit 1; \
	fi
	@echo "Dropping and recreating database..."
	@PGPASSWORD=$(DB_PASSWORD) psql -h localhost -U $(DB_USERNAME) -d postgres -c "DROP DATABASE IF EXISTS journals;" 2>/dev/null || true
	@PGPASSWORD=$(DB_PASSWORD) psql -h localhost -U $(DB_USERNAME) -d postgres -c "CREATE DATABASE journals;" 2>/dev/null || \
		(echo "Failed to recreate database. Check DB_USERNAME/DB_PASSWORD in .env file." && exit 1)
	@echo "✅ Database reset complete! Tables will be created on next app start."

.PHONY: db-backup
db-backup: ## Backup database to SQL file
	@echo "Creating database backup..."
	@backup_file="journals-$(shell date +%Y%m%d_%H%M%S).sql"; \
	PGPASSWORD=$(DB_PASSWORD) pg_dump -h localhost -U $(DB_USERNAME) journals > "$$backup_file" 2>/dev/null || \
		(echo "Failed to create backup. Check DB_USERNAME/DB_PASSWORD in .env file." && exit 1); \
	echo "✅ Database backed up to: $$backup_file"

.PHONY: db-restore
db-restore: ## Restore database from SQL backup file
	@echo "Available backups:"
	@ls -la journals-*.sql 2>/dev/null || echo "No backups found"
	@read -p "Enter backup filename: " backup; \
	if [ -f "$$backup" ]; then \
		echo "⚠️  WARNING: This will replace all data in the 'journals' database!"; \
		read -p "Are you sure? Type 'yes' to continue: " confirm; \
		if [ "$$confirm" != "yes" ]; then \
			echo "Cancelled."; \
			exit 1; \
		fi; \
		echo "Restoring from $$backup..."; \
		PGPASSWORD=$(DB_PASSWORD) psql -h localhost -U $(DB_USERNAME) -d journals < "$$backup" 2>/dev/null || \
			(echo "Failed to restore database. Check DB_USERNAME/DB_PASSWORD in .env file." && exit 1); \
		echo "✅ Database restored from $$backup"; \
	else \
		echo "Error: Backup file not found"; \
		exit 1; \
	fi

# Health and monitoring
.PHONY: health
health: ## Check application health
	@echo "Checking application health..."
	@if curl -s --connect-timeout 5 http://localhost:8080/health >/dev/null 2>&1; then \
		echo "Application is running, fetching health status..."; \
		curl -s http://localhost:8080/health | jq . 2>/dev/null || curl -s http://localhost:8080/health; \
	else \
		echo "❌ Application is not running on http://localhost:8080"; \
		echo "💡 Start the application with: make run"; \
	fi

# Docker and Google Cloud Functions tasks
.PHONY: docker-build
docker-build: ## Build Docker image for Google Cloud Functions
	@echo "Building Docker image for Google Cloud Functions..."
	@docker build -t tg-journals:latest .
	@echo "✅ Docker image built successfully!"

.PHONY: docker-run
docker-run: ## Run Docker container locally
	@echo "Running Docker container locally..."
	@mkdir -p $(PWD)/data
	@docker run -p 8080:8080 --env-file .env -v $(PWD)/data:/mnt/disk tg-journals:latest

.PHONY: docker-stop
docker-stop: ## Stop Docker containers using port 8080
	@echo "Stopping Docker containers using port 8080..."
	@docker ps -q --filter "publish=8080" | xargs -r docker stop

.PHONY: gcp-deploy
gcp-deploy: ## Deploy to Google Cloud Functions using Pulumi
	@echo "Deploying to Google Cloud Functions..."
	@if [ -z "$(GCP_PROJECT_ID)" ]; then \
		echo "ERROR: GCP_PROJECT_ID not set in .env file"; \
		exit 1; \
	fi
	@mkdir -p .pulumi-state
	# Use local filesystem state for Pulumi. OK for one developer.
	@cd pulumi && if [ -d "venv" ]; then . venv/bin/activate; fi && pulumi login file://../.pulumi-state && (pulumi stack select dev || pulumi stack init dev) && pulumi config set gcp:project $(GCP_PROJECT_ID) --stack dev && pulumi config set gcp:region $(GCP_REGION) --stack dev && pulumi up --yes
	@echo "✅ Deployment completed successfully!"

.PHONY: gcp-status
gcp-status: ## View Google Cloud Run v2 status
	@echo "Fetching Google Cloud Run v2 status..."
	@if [ -z "$(GCP_PROJECT_ID)" ]; then \
		echo "ERROR: GCP_PROJECT_ID not set in .env file"; \
		exit 1; \
	fi
	@gcloud run services describe tg-journals-function --project=$(GCP_PROJECT_ID) --region=$(GCP_REGION)

.PHONY: gcp-logs
gcp-logs: ## View Google Cloud Run v2 logs
	@echo "Fetching Google Cloud Run v2 logs..."
	@if [ -z "$(GCP_PROJECT_ID)" ]; then \
		echo "ERROR: GCP_PROJECT_ID not set in .env file"; \
		exit 1; \
	fi
	@gcloud run services logs read $(FUNCTION_NAME) --region=$(GCP_REGION) --project=$(GCP_PROJECT_ID) --limit=50

.PHONY: gcp-delete
gcp-delete: ## Delete Google Cloud resources.
	@echo "Deleting Google Cloud Run v2 deployment..."
	@mkdir -p .pulumi-state
	@cd pulumi && if [ -d "venv" ]; then . venv/bin/activate; fi && pulumi login file://../.pulumi-state && (pulumi stack select dev || pulumi stack init dev) && pulumi destroy
	@echo "✅ Deployment deleted successfully!"

# Generate native image hints using GraalVM tracing agent.
.PHONY: native-update-hints
native-update-hints:
	@echo "🔍 Generating native image hints with GraalVM tracing agent..."
	@echo ""
	@echo "📋 Instructions:"
	@echo "  1. The app will start in JVM mode with tracing enabled"
	@echo "  2. Send a Telegram messages to make your bot use all features"
	@echo "  3. Test any other app features"
	@echo "  4. Press Ctrl+C when done"
	@echo ""
	@./gradlew generateNativeHints || true
	@echo ""
	@echo "✅ Hints generated/merged into:"
	@echo "   src/main/resources/META-INF/native-image/com.aleksandrmakarov/tg-journals/"
	@echo ""
	@echo "📦 Next steps:"
	@echo "   1. Review the generated JSON files (optional)"
	@echo "   2. Rebuild Docker image: make docker-build"
	@echo "   3. Test the native image: make docker-run"

.PHONY: native-build
native-build: ## Build GraalVM native image locally
	@echo "Building GraalVM native image..."
	@./gradlew nativeCompile
	@echo "✅ Native image built successfully!"

.PHONY: native-run
native-run: ## Run native image locally (with local database)
	@echo "Running native image locally..."
	@./build/native/nativeCompile/tg-journals

.PHONY: full-deploy
full-deploy: native-build docker-build gcp-deploy ## Complete deployment pipeline
	@echo "✅ Full deployment pipeline completed!"

.PHONY: quick-deploy
quick-deploy: docker-build gcp-deploy ## Quick deployment (assumes native image already built)
	@echo "✅ Quick deployment completed!"
