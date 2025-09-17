docker compose -f .\infra\docker-compose\dev.yml up -d

$rp = (docker ps --filter "ancestor=docker.redpanda.com/redpandadata/redpanda:latest" --format "{{.Names}}")

docker exec -it $rp rpk topic consume acq.job.dispatched --brokers 127.0.0.1:9092 --offset oldest
