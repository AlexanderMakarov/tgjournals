# Deployment Instructions

## Environment Variables

Set the following environment variables:

- `TELEGRAM_BOT_TOKEN`: Your Telegram bot token from @BotFather
- `TELEGRAM_BOT_USERNAME`: Your bot username (without @)
- `TELEGRAM_WEBHOOK_URL`: Webhook URL for production (optional)
- `PORT`: Server port (default: 8080)

## Local Development

1. Set environment variables:
```bash
export TELEGRAM_BOT_TOKEN="your_bot_token_here"
export TELEGRAM_BOT_USERNAME="your_bot_username"
```

2. Run the application:
```bash
./gradlew bootRun
```

## Production Deployment

### Kubernetes

1. Create a ConfigMap with your bot credentials
2. Deploy with the health check endpoint: `/health`
3. Configure webhook URL if using webhook mode instead of polling

### Docker

```dockerfile
FROM openjdk:17-jre-slim
COPY build/libs/journals-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

## Database

The application uses SQLite with WAL mode and foreign keys enabled. The database file `journals.db` will be created automatically.

## Bot Commands

### For Coaches:
- `/questions` - Set session questions
- `/participants` - View all players

### For Players:
- `/before` - Answer pre-session questions
- `/after` - Answer post-session questions
- `/last5` - View last 5 journals
- `/last` - View last journal
- `/history` - View all journals

## Health Check

The application provides a `/health` endpoint for Kubernetes liveness checks.
