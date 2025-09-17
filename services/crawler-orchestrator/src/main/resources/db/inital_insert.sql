insert into ing.portal_policy (portal, max_concurrency, target_qps, bucket_size, max_attempts, backoff_sec)
values ('idealista', 2, 0.20, 3, 4, '{86400, 172800, 604800, 1209600}')
on conflict (portal) do update set
  max_concurrency = excluded.max_concurrency,
  target_qps = excluded.target_qps,
  bucket_size = excluded.bucket_size,
  max_attempts = excluded.max_attempts,
  backoff_sec = excluded.backoff_sec;
