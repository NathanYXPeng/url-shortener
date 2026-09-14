# URL Shortener

A small REST API that shortens URLs, caches lookups in Redis, and tracks
click counts asynchronously through RabbitMQ.

## Why it's built this way

**Redirect is on the user-facing hot path; click counting is not.**
`GET /{shortCode}` looks up the original URL (Redis cache-aside in front of
Postgres) and returns a 302 immediately. It does **not** wait for the click
count to be written to the database — that would tie redirect latency to
database write latency for no reason the user cares about.

Instead, the redirect handler publishes a `ClickEvent` to RabbitMQ and
returns. A separate listener consumes the event and increments the counter.
This means:
- A slow or temporarily-down database doesn't break redirects.
- A burst of clicks on one link doesn't turn into a burst of concurrent
  `UPDATE` statements on the same row.

**RabbitMQ gives at-least-once delivery, so the consumer has to be
idempotent.** If the consumer crashes after writing to the DB but before
acking, RabbitMQ will redeliver the message. `ClickEventListener` guards
against double-counting with a Redis `SETNX`-style check keyed on the
event's UUID (`click:processed:<eventId>`, 24h TTL) — the first delivery
wins, redeliveries are detected and skipped.

**Failed messages don't retry forever.** The queue is configured with a
dead-letter exchange/queue. If processing a message throws, the listener
nacks it without requeueing, and RabbitMQ routes it to `click-events-dlq`
instead of looping forever or blocking the queue.

## Stack

Spring Boot 3 · PostgreSQL · Flyway · Redis · RabbitMQ · springdoc-openapi (Swagger)

## Running locally

```bash
docker-compose up -d        # Postgres, Redis, RabbitMQ
mvn spring-boot:run          # the app itself
```

- API base: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- RabbitMQ management UI: `http://localhost:15672` (guest/guest)

## API

| Method | Path                          | Description                          |
|--------|-------------------------------|---------------------------------------|
| POST   | `/api/urls`                   | Create a short URL                    |
| GET    | `/{shortCode}`                 | 302 redirect + async click tracking   |
| GET    | `/api/urls/{shortCode}/stats` | Click count and metadata              |

```bash
curl -X POST localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://example.com/some/long/path"}'

curl -i localhost:8080/<shortCode>

curl localhost:8080/api/urls/<shortCode>/stats
```

## Architecture

```
Client -> Controller -> Service -> Redis (cache-aside) / Postgres
                              |
                              +--> RabbitMQ (click-event, at-least-once)
                                        |
                                        v
                              Listener (manual ack, idempotent via Redis)
                                        |
                                        +--> Postgres (click_count += 1)
                                        +--> DLQ on repeated failure
```
