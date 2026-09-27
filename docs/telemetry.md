# Service Telemetry

## Service onboarding

OWNER creates a service at `POST /api/v1/telemetry/services`. The response includes `serviceId`, metadata and an API key exactly once. The database stores only a SHA-256 hash. Copy the key to the service's secret manager; it cannot be retrieved later. Regenerate immediately invalidates the old key. Revoke invalidates the current key. Disabled services reject ingestion while retaining their history.

## Ingestion contract

Send `Authorization: Bearer dash_sk_...` to `/api/v1/telemetry/events`, `/gauges` or `/batch`. This auth is independent of the dashboard OWNER cookie. Timestamp is optional UTC/offset ISO-8601 and defaults to server time; accepted timestamps are no more than 30 days old and five minutes in the future. Requests are limited to 64 KiB and 120 per service per minute. A batch accepts at most 100 events and 100 gauges.

Events contain `type`, optional `timestamp`, optional `anonymousUserId`, and at most 24 scalar `properties` fields, with a serialized properties limit of 4 KiB. `request` properties convention: `endpoint`, `method`, numeric `status`, numeric `latencyMs`. Request responses with status 400–599 and explicit `error` events contribute to error counts. `user_activity` should use a service-specific salted SHA-256 pseudonymous identifier; never send emails, Discord IDs, usernames, or other direct identifiers. Gauges contain a 64-character metric `name`, non-negative finite `value`, and optional timestamp.

```json
{"type":"request","timestamp":"2026-09-27T22:30:14+09:00","properties":{"endpoint":"/api/team-analysis","method":"POST","status":200,"latencyMs":183}}
```

## Integration safety

Telemetry must not be on the service response path. Schedule network sends asynchronously on a bounded executor/task, set a short timeout (about 1–2 seconds), catch and ignore telemetry failures, and optionally buffer locally for later batch delivery. Never synchronously wait for this dashboard before returning a user response. Integration examples in the Telemetry app use this fire-and-forget pattern and read the key from an environment secret.

## Storage and analytics

Raw event/gauge rows are separate from incremental hourly and daily request/error/latency sum/count aggregates. Request totals, error rates and average latency use hourly aggregates with only partial edge buckets read from raw events. Endpoint/method/status distributions and p50/p95/p99 use request samples in the selected range; this initial SQLite implementation is intended for a personal dashboard workload. DAU/WAU/MAU use distinct pseudonymous IDs in rolling 24-hour/7-day/30-day windows. Peak concurrent users is calculated from `active_users` or `concurrent_users` gauges when either is present. Missing measurements are returned as null and rendered as “no data”. Gauge views use the newest sample from the last 30 days.

## Privacy and operations

The dashboard never needs the real identity of an external service user. Create a unique random salt per service and compute `SHA-256(service-specific-salt + internal-user-id)` before sending activity. Keep salts and source IDs only in the external service. API keys and hashes are never returned by service detail/list endpoints. Disable or revoke keys if a service is retired or a key may have leaked.
