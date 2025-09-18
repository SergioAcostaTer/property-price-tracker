-- ===== Schema changes (remove scheduling via next_run_at) =====
alter table ing.frontier
  drop column if exists next_run_at,
  add column if not exists last_run_at timestamptz,
  add column if not exists last_result_status int;

-- Drop old due index if it exists
drop index if exists idx_frontier_due;

-- Helpful indexes for new flow
create index if not exists idx_frontier_priority on ing.frontier (portal, priority, status);
create index if not exists idx_frontier_last_run on ing.frontier (portal, task_type, last_run_at);
create index if not exists idx_frontier_lease on ing.frontier (portal, task_type, lease_until) where lease_until is not null;

-- ===== Portal policy: add min-days-between-runs (global per portal) =====
alter table ing.portal_policy
  add column if not exists min_days_between_runs int not null default 7;

-- ===== Seed/update a portal policy (Idealista) =====
insert into ing.portal_policy (portal, max_concurrency, target_qps, bucket_size, max_attempts, backoff_sec, min_days_between_runs)
values ('idealista', 2, 0.20, 3, 4, '{86400,172800,604800,1209600}', 7)
on conflict (portal) do update set
  max_concurrency = excluded.max_concurrency,
  target_qps = excluded.target_qps,
  bucket_size = excluded.bucket_size,
  max_attempts = excluded.max_attempts,
  backoff_sec = excluded.backoff_sec,
  min_days_between_runs = excluded.min_days_between_runs;

-- ===== Canary Islands seeds (search pages) =====
-- Segments: rent, sale, room (rooms)
-- Task type: search_page
-- Priority: lower = higher priority. Use 2 for core seeds.
-- last_run_at left NULL so they are eligible ASAP (rate-limited by policy).
insert into ing.frontier (
  portal, task_type, url, url_hash, segment, priority, status, dedupe_key, scope, first_seen_at, meta
)
values
  -- Las Palmas
  ('idealista','search_page','https://www.idealista.com/alquiler-viviendas/las-palmas/', md5('https://www.idealista.com/alquiler-viviendas/las-palmas/'), 'rent', 2, 'active', 'idealista:rent:las-palmas', '{}'::jsonb, now(), '{}'::jsonb),
  ('idealista','search_page','https://www.idealista.com/venta-viviendas/las-palmas/',    md5('https://www.idealista.com/venta-viviendas/las-palmas/'),    'sale', 2, 'active', 'idealista:sale:las-palmas', '{}'::jsonb, now(), '{}'::jsonb),
  ('idealista','search_page','https://www.idealista.com/alquiler-habitacion/las-palmas/', md5('https://www.idealista.com/alquiler-habitacion/las-palmas/'), 'room', 2, 'active', 'idealista:room:las-palmas', '{}'::jsonb, now(), '{}'::jsonb),

  -- Santa Cruz de Tenerife
  ('idealista','search_page','https://www.idealista.com/alquiler-viviendas/santa-cruz-de-tenerife/', md5('https://www.idealista.com/alquiler-viviendas/santa-cruz-de-tenerife/'), 'rent', 2, 'active', 'idealista:rent:sct', '{}'::jsonb, now(), '{}'::jsonb),
  ('idealista','search_page','https://www.idealista.com/venta-viviendas/santa-cruz-de-tenerife/',    md5('https://www.idealista.com/venta-viviendas/santa-cruz-de-tenerife/'),    'sale', 2, 'active', 'idealista:sale:sct', '{}'::jsonb, now(), '{}'::jsonb),
  ('idealista','search_page','https://www.idealista.com/alquiler-habitacion/santa-cruz-de-tenerife/', md5('https://www.idealista.com/alquiler-habitacion/santa-cruz-de-tenerife/'), 'room', 2, 'active', 'idealista:room:sct', '{}'::jsonb, now(), '{}'::jsonb)
on conflict (portal, task_type, url_hash) do nothing;

-- Idempotency event log for listener (minimal)
create table if not exists ing.event_log(
  event_id    uuid primary key,
  topic       text not null,
  received_at timestamptz not null default now()
);
