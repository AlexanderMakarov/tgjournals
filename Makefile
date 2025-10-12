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
