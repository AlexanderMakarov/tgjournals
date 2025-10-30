# Makefile for Telegram Journals Bot

# Load environment variables from .env file
include .env
export

# Development tasks
.PHONY: run
run: ## Run the application
	./gradlew bootRun

.PHONY: test
test: ## Run tests
	./gradlew test

.PHONY: test-coverage
test-coverage: ## Run tests with coverage report
	./gradlew test jacocoTestReport

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
			curl -s -X POST "https://api.telegram.org/bot$(TELEGRAM_BOT_TOKEN)/setWebhook" \
				-H "Content-Type: application/json" \
				-d "{\"url\": \"$$url/webhook\"}" > /dev/null; \
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
.PHONY: db-reset
db-reset: ## Reset database (delete and recreate)
	@echo "Resetting database..."
	@rm -f journals.db*
	@echo "Database reset complete!"

.PHONY: db-backup
db-backup: ## Backup database (commits WAL changes first)
	@echo "Creating database backup..."
	@echo "Committing WAL changes to main database..."
	@sqlite3 journals.db "PRAGMA wal_checkpoint(FULL);"
	@echo "Creating backup..."
	@cp journals.db journals-$(shell date +%Y%m%d_%H%M%S).db
	@echo "Database backed up!"

.PHONY: db-restore
db-restore: ## Restore database from backup
	@echo "Available backups:"
	@ls -la journals-*.db 2>/dev/null || echo "No backups found"
	@read -p "Enter backup filename: " backup; \
	if [ -f "$$backup" ]; then \
		cp "$$backup" journals.db; \
		echo "Database restored from $$backup"; \
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
	@cd pulumi && pulumi up --yes
	@echo "✅ Deployment completed successfully!"

.PHONY: gcp-logs
gcp-logs: ## View Google Cloud Functions logs
	@echo "Fetching Google Cloud Functions logs..."
	@if [ -z "$(GCP_PROJECT_ID)" ]; then \
		echo "ERROR: GCP_PROJECT_ID not set in .env file"; \
		exit 1; \
	fi
	@gcloud functions logs read tg-journals-function --project=$(GCP_PROJECT_ID) --limit=50

.PHONY: gcp-delete
gcp-delete: ## Delete Google Cloud Functions deployment
	@echo "Deleting Google Cloud Functions deployment..."
	@cd pulumi && pulumi destroy --yes
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
native-run: ## Run native image locally
	@echo "Running native image locally..."
	@./build/native/nativeCompile/tg-journals

.PHONY: full-deploy
full-deploy: native-build docker-build gcp-deploy ## Complete deployment pipeline
	@echo "✅ Full deployment pipeline completed!"

.PHONY: quick-deploy
quick-deploy: docker-build gcp-deploy ## Quick deployment (assumes native image already built)
	@echo "✅ Quick deployment completed!"
