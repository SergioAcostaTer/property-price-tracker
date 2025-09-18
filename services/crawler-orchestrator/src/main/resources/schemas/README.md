Versioned JSON Schemas for Kafka events.

- acq.job.dispatched.v1.schema.json  → events produced by CO to request scraping
- acq.raw.page.v1.schema.json        → events produced by Scraper with page fetch result

Validation happens on:
- Producer side (CO validates before enqueueing to outbox)
- Consumer side (CO validates raw page events before DB writes)

Breaking changes → bump to v2 and keep v1 for backward compatibility.
