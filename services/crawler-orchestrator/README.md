# Crawler Orchestrator (CO)

A Spring Boot service that orchestrates web crawling jobs for property price tracking. The CO manages a crawling frontier, dispatches jobs to scrapers via Kafka, and processes crawling results.

## Architecture Overview

The Crawler Orchestrator is the central coordination service in a distributed crawling system:

- **Frontier Management**: Maintains URLs to crawl with priority, scheduling, and deduplication
- **Job Dispatching**: Creates crawling jobs and sends them via Kafka to scraper services
- **Result Processing**: Receives crawling results and updates the frontier with discovered links
- **Rate Limiting**: Enforces per-portal crawling policies (QPS, concurrency limits)
- **Leader Election**: Ensures only one CO instance dispatches jobs (Redis-based)

## Key Components

### Frontier System
- **Frontier Table**: Stores URLs to crawl with metadata (priority, segment, last run time)
- **Job Table**: Tracks dispatched crawling jobs and their status
- **Portal Policies**: Configurable rate limits and concurrency controls per website

### Event-Driven Architecture
- **Outbox Pattern**: Reliable Kafka message publishing with transactional guarantees
- **Job Dispatched Events**: Sent to scrapers with crawling instructions
- **Raw Page Events**: Received from scrapers with crawling results and discovered links

### Core Services
- **FrontierDispatcher**: Main scheduler that claims due URLs and creates jobs
- **PageResultListener**: Processes scraper results and updates frontier
- **OutboxRelay**: Publishes queued events to Kafka
- **RedisTokenBucket**: Rate limiting implementation
- **Watchdog**: Releases stuck leases and cleanup tasks

## Prerequisites

- Java 21+
- PostgreSQL 16+
- Redis 7+
- Apache Kafka (or Redpanda)

## Quick Start

### 1. Start Infrastructure

Using the provided Docker Compose:

```bash
cd infra/docker-compose
docker-compose -f dev.yml up -d
```

This starts:
- PostgreSQL on port 5432
- Redis on port 6379  
- Redpanda (Kafka) on port 9092
- Redpanda Console on port 8082

### 2. Build and Run

```bash
cd services/crawler-orchestrator
./gradlew bootRun
```

The service starts on port 8080 with:
- Health checks at `/actuator/health`
- Metrics at `/actuator/prometheus`
- API endpoints under `/v1/`

### 3. Seed Initial URLs

The database migration automatically seeds some Canary Islands property search pages for Idealista:

```sql
-- Example seeded URLs
INSERT INTO ing.frontier (portal, task_type, url, url_hash, segment, priority, status)
VALUES 
  ('idealista', 'search_page', 'https://www.idealista.com/alquiler-viviendas/las-palmas/', 
   md5('https://www.idealista.com/alquiler-viviendas/las-palmas/'), 'rent', 2, 'active');
```

## Configuration

### Database

Configure PostgreSQL connection in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/app
    username: app
    password: app
```

### Kafka

Configure Kafka brokers:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: co-result-handler
```

### Authentication

The service includes token-based authentication:

```yaml
co:
  auth:
    token: "your-secret-token"
    enabled: true
```

Send requests with: `Authorization: Bearer your-secret-token`

## API Usage

### Add URLs to Frontier

```bash
curl -X POST http://localhost:8080/v1/frontier/batch-upsert \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer dev-token" \
  -d '{
    "portal": "idealista",
    "resources": [
      {
        "task_type": "search_page",
        "segment": "rent",
        "url": "https://www.idealista.com/alquiler-viviendas/madrid/",
        "priority": 3,
        "dedupe_key": "idealista:rent:madrid"
      }
    ]
  }'
```

### Response

```json
{
  "upserted": 1
}
```

## How It Works

### Dispatching Flow

1. **FrontierDispatcher** runs every second on the leader instance
2. For each portal, it:
   - Checks concurrency limits (max concurrent jobs)
   - Applies rate limiting via Redis token bucket
   - Claims due URLs from frontier (respects `min_days_between_runs`)
   - Creates job records and outbox events
3. **OutboxRelay** publishes events to Kafka topic `acq.job.dispatched`

### Result Processing

1. Scrapers send results to Kafka topic `acq.raw.page`
2. **PageResultListener** processes each result:
   - Updates job status (succeeded/retry/failed)
   - Updates frontier with last run time and status
   - Extracts discovered URLs from search pages
   - Adds new URLs to frontier for future crawling

### Rate Limiting

Each portal has configurable policies:

```sql
INSERT INTO ing.portal_policy (portal, max_concurrency, target_qps, bucket_size)
VALUES ('idealista', 2, 0.20, 3);
```

- `max_concurrency`: Max simultaneous jobs per portal
- `target_qps`: Requests per second limit
- `bucket_size`: Token bucket capacity for bursts
- `min_days_between_runs`: Minimum days between crawling same URL

## Event Schemas

### Job Dispatched Event

```json
{
  "schema_version": 1,
  "event_id": "uuid",
  "occurred_at": "2024-01-01T12:00:00Z",
  "job": {
    "job_id": "uuid",
    "portal": "idealista", 
    "task_type": "search_page",
    "segment": "rent",
    "priority": 2
  },
  "request": {
    "url": "https://example.com/search",
    "url_hash": "md5hash",
    "attempt": 1
  }
}
```

### Raw Page Event (from scrapers)

```json
{
  "schema_version": 1,
  "event_id": "uuid",
  "occurred_at": "2024-01-01T12:00:00Z",
  "job": {
    "job_id": "uuid",
    "portal": "idealista",
    "task_type": "search_page",
    "segment": "rent"
  },
  "request": {
    "url": "https://example.com/search",
    "url_hash": "md5hash"
  },
  "http": {
    "status": 200,
    "content_hash": "md5hash",
    "fetched_at": "2024-01-01T12:00:00Z"
  },
  "discovered": [
    {
      "url": "https://example.com/property/123",
      "task_type": "detail",
      "segment": "rent",
      "priority": 5
    }
  ]
}
```

## Database Schema

The CO uses PostgreSQL with custom enums and JSONB columns:

- `ing.frontier`: URLs to crawl with scheduling metadata
- `ing.job`: Dispatched job tracking
- `ing.portal_policy`: Per-portal crawling policies  
- `ing.outbox`: Reliable event publishing queue
- `ing.event_log`: Event deduplication

Key indexes support efficient frontier querying by priority and scheduling.

## Monitoring

### Health Checks

- `/actuator/health` - Service health status
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus format metrics

### Key Metrics to Monitor

- Frontier queue depth by portal/segment
- Job dispatch rate and success rate  
- Kafka consumer lag
- Redis token bucket hit rate
- Database connection pool usage

### Logging

The service uses structured logging with:
- Job dispatch events
- Rate limiting decisions
- Error handling and retries
- Leader election status

## Production Considerations

### Scaling

- Run multiple CO instances for high availability
- Only one instance acts as leader (Redis-based election)
- Scale Kafka consumers independently
- Use read replicas for frontier queries

### Configuration

- Increase Kafka partitions for higher throughput
- Tune PostgreSQL for your workload
- Configure appropriate rate limits per portal
- Set up monitoring and alerting

### Security

- Use strong authentication tokens
- Restrict network access to infrastructure
- Enable TLS for all connections
- Rotate credentials regularly

## Development

### Running Tests

```bash
./gradlew test
```

Tests use Testcontainers for integration testing with real PostgreSQL and Kafka.

### Code Structure

- `app/` - Core business logic and services
- `api/` - REST controllers and DTOs  
- `domain/` - Entities, enums, and repositories
- `config/` - Spring configuration classes
- `schema/` - JSON schema validation
- `util/` - Utility classes

### Contributing

1. Follow existing code style and patterns
2. Add tests for new functionality
3. Update documentation for API changes
4. Use meaningful commit messages