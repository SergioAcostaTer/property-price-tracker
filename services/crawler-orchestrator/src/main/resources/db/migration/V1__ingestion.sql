-- ============ ingestion schema (CO-owned) ============
create schema if not exists ing;

-- enums
create type ing.ing_task_type        as enum ('search_page','detail');
create type ing.ing_frontier_status  as enum ('active','paused','quarantined','retired');
create type ing.ing_segment          as enum ('unknown','sale','rent','short_term','room','commercial','land');
create type ing.ing_job_status       as enum ('dispatched','succeeded','retry','failed');

-- -------- frontier (what to crawl) --------
create table if not exists ing.frontier(
  portal                text not null,
  task_type             ing.ing_task_type not null,
  url                   text not null,
  url_hash              char(32) not null,                -- md5(canonical_url)
  segment               ing.ing_segment not null default 'unknown',
  priority              int not null default 5,           -- 1..9
  next_run_at           timestamptz not null,
  lease_until           timestamptz,
  status                ing.ing_frontier_status not null default 'active',
  dedupe_key            text,
  scope                 jsonb not null default '{}'::jsonb,
  first_seen_at         timestamptz not null default now(),
  last_dispatched_at    timestamptz,
  last_success_at       timestamptz,
  consecutive_failures  int not null default 0,
  meta                  jsonb not null default '{}'::jsonb,
  constraint pk_frontier primary key (portal, task_type, url_hash),
  constraint chk_frontier_priority_range check (priority between 1 and 9)
);

-- due queue for dispatcher
create index if not exists idx_frontier_due
  on ing.frontier (portal, task_type, segment, next_run_at, priority)
  where status = 'active';

-- watchdog helper
create index if not exists idx_frontier_lease
  on ing.frontier (portal, task_type, lease_until)
  where lease_until is not null;

-- optional JSONB lookups
create index if not exists idx_frontier_scope_gin on ing.frontier using gin (scope);

-- prevent dupes when seeding
create unique index if not exists ux_frontier_dedupe_active
  on ing.frontier (portal, task_type, dedupe_key)
  where dedupe_key is not null and status = 'active';

-- -------- job (dispatch log) --------
create table if not exists ing.job(
  job_id         uuid primary key,
  portal         text not null,
  task_type      ing.ing_task_type not null,
  segment        ing.ing_segment not null default 'unknown',
  url_hash       char(32) not null,
  url            text not null,
  attempt        int  not null default 1,
  status         ing.ing_job_status not null default 'dispatched',
  scheduled_at   timestamptz not null,
  last_update_at timestamptz not null default now(),
  hints          jsonb not null default '{}'::jsonb
);

-- fast inflight count
create index if not exists idx_job_inflight_portal
  on ing.job (portal)
  where status = 'dispatched';

create index if not exists idx_job_status_time
  on ing.job (status, last_update_at);

-- -------- single policy table (no per-segment) --------
create table if not exists ing.portal_policy(
  portal           text primary key,
  max_concurrency  int          not null default 4,
  target_qps       numeric(5,2) not null default 0.40,
  bucket_size      int          not null default 6,
  max_attempts     int          not null default 4,
  backoff_sec      int[]        not null default '{60,300,1800,3600}'
);

-- -------- outbox --------
create table if not exists ing.outbox(
  id          bigserial primary key,
  topic       text  not null,
  k           bytea,
  v           jsonb not null,
  headers     jsonb not null default '{}'::jsonb,
  created_at  timestamptz default now(),
  sent_at     timestamptz,
  attempts    int not null default 0,
  last_error  text
);