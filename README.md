# Telegram Bot for Journals

This is a Telegram bot for managing soccer players' journals.

Idea: admin (coach) makes list of questions before traning session and after it - both parts make a journal per session (day). Questions require free form answer (text).
Bot allows players to participate in this quiz and saves answers to the journal.

Journals are available to read for players themselves and admin (coach) could read them for all players.

## Specs

Java 17, Spring Boot 3.5.6, SQLite in WAL mode with foreign keys enabled, JdbcTemplate, LogBACK, OpenAPI (springdoc), telegram bot via webhook.

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
- Players answer on questions one-by-one, "before or after" information only makes 2 groups of questions, "before" questions for "/before" command, "after" questions for "/after" command. Flow is - run "/before" before the session, answer on questions one-by-one, at the end get something like "Done for now, good luck with the sesssion, run `/after` command once you finish it." from the bot and follow this instruction.
- Players may see theirs journals for the last 5 sessions via `/last5` command. Each journal starts with date label, next question, colon, answer. `/last` command should return only the last journal. With `/history` command players should get all journals (could be a quite big sequience with message per 5 journals).
- Admin may get list of players participating in journals with `/participants`. It should return list of telegram users sorted chronologically by last journal date. List items should be press-able. Each participant should be represented with telegram nickname, first and last name, last journal time, total number of journals. Press on participant should return 5 last journals.

# Setup

## 1. Create a Telegram Bot

1. Open Telegram and search for `@BotFather`
2. Send `/newbot` command
3. Follow the prompts to create your bot:
   - Choose a name for your bot (e.g., "Journals Bot")
   - Choose a username (must end with 'bot', e.g., "journals_soccer_bot")
4. Save the **Bot Token** provided by BotFather
5. Save the **Bot Username** (without @)
6. Create `.env` file by copying [`.env.example`](/.env.example) and filling in the values:
   ```bash
   cp .env.example .env
   # Edit .env with your bot credentials and webhook secret
   ```

## 2. Local Development Setup

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

## 3. Production Deployment

For production, deploy your application to a server with a public domain and set the webhook:

```bash
# Set production webhook (will ask for your domain URL)
make set-prod
```

### Webhook Security

**Important**: The webhook endpoint is protected by a secret token to prevent unauthorized access.

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

## 4. Webhook Management

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

## 5. Verify Setup

1. Start the application: `make run`
2. Check the health endpoint: `make health`
3. Set the webhook URL: `make set-local` (for development) or `make set-prod` (for production)
4. Send `/start` to your bot in Telegram
5. Check webhook status: `make check-webhook`

## 6. Available Endpoints

- **Health Check**: `GET /health` - Returns database statistics with caching
- **API Documentation**: `GET /docs` - Swagger UI
- **Webhook**: `POST /webhook` - Telegram webhook endpoint

## 7. Makefile Commands

The project includes a simplified Makefile for common development tasks:

### Development Commands
```bash
make run               # Run the application
make test              # Run tests
make test-coverage     # Run tests with coverage report
make format            # Fix code formatting
make clean             # Clean build artifacts
```

### Webhook Setup (Automated)
```bash
make set-local         # Start ngrok and set webhook automatically
make set-prod          # Set production webhook (asks for URL)
make check-webhook     # Check current webhook status
make delete-webhook    # Delete webhook (stop receiving updates)
```

### Database Commands
```bash
make db-reset          # Reset database (delete and recreate)
make db-backup         # Backup database
make db-restore        # Restore database from backup
```

### Health and Monitoring
```bash
make health            # Check application health
```

## 8. Security Considerations

### Webhook Security

The webhook endpoint (`/webhook`) is protected by a secret token to prevent unauthorized access:

- **Development**: If no `TELEGRAM_WEBHOOK_SECRET` is set, webhook accepts all requests
- **Production**: Must set `TELEGRAM_WEBHOOK_SECRET` for security
- **Validation**: Each request is validated against the secret token
- **Logging**: Unauthorized attempts are logged with IP addresses

### Environment Variables Security

Never commit your `.env` file to version control:

```bash
# .env is already in .gitignore
# Keep your secrets secure:
TELEGRAM_BOT_TOKEN=your_secret_token
TELEGRAM_WEBHOOK_SECRET=your_webhook_secret
```

### Production Deployment Security

1. **Use HTTPS**: Always use HTTPS in production
2. **Set webhook secret**: Configure `TELEGRAM_WEBHOOK_SECRET`
3. **Monitor logs**: Watch for unauthorized access attempts
4. **Regular updates**: Keep dependencies updated
5. **Database security**: SQLite file permissions should be restricted
