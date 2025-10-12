# Telegram Bot for Journals

This is a Telegram bot for managing soccer players' journals.

Idea: admin (coach) makes list of questions before traning session and after it - both parts make a journal per session (day). Questions require free form answer (text).
Bot allows players to participate in this quiz and saves answers to the journal.

Journals are available to read for players themselves and admin (coach) could read them for all players.

## Specs

Java 17, Spring Boot 3.5.6, SQLite in WAL mode with foreign keys enabled, JdbcTemplate, LogBACK, OpenAPI (springdoc), telegram bot via webhook.

## Features

- 2 roles: coach and player.
- Only one set of questions for the session could be active - it is avaialable to take by players any time.
- Coach writes questions in the format - one question per line, if line starts with "Before: " then this is a question before (and this prefix is not kept in the question), if starts with "After: " then this is question after. Everything should fit into one message. For example coach runs `/questions` command and after "Please update session questions" invite from the bot provides following message:
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
- Coach may get list of players participating in journals with `/participants`. It should return list of telegram users sorted chronologically by last journal date. List items should be press-able. Each participant should be represented with telegram nickname, first and last name, last journal time, total number of journals. Press on participant should return 5 last journals.
