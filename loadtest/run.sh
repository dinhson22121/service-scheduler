#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

script=${1:?script name, e.g. mixed-ramp}
export RUN_LABEL=${2:?run label, e.g. 2r-pool10}
results=loadtest/results
compose=(docker compose -f docker-compose.yml -f docker-compose.loadtest.yml)

if [[ ${REPLICAS:-2} == 1 ]]; then
    export NGINX_CONF=./loadtest/nginx/one-replica.conf ACTUATORS=http://app1:8081
    services=(postgres app1 nginx)
else
    export NGINX_CONF=./loadtest/nginx/two-replicas.conf ACTUATORS=http://app1:8081,http://app2:8081
    services=(postgres app1 app2 nginx)
fi

echo ">> fresh stack: ${services[*]} (pool ${DB_POOL_SIZE:-10})"
"${compose[@]}" --profile loadtest down -v --remove-orphans >/dev/null 2>&1
"${compose[@]}" up -d --wait "${services[@]}"

{
    echo "label=$RUN_LABEL script=$script replicas=${REPLICAS:-2} pool=${DB_POOL_SIZE:-10} commit=$(git rev-parse --short HEAD)"
    echo "docker_vm_cpus=$(docker info --format '{{.NCPU}}') docker_vm_mem_bytes=$(docker info --format '{{.MemTotal}}')"
} > "$results/$RUN_LABEL-env.txt"

stats="$results/$RUN_LABEL-stats.csv"
echo "epoch,container,cpu_pct,mem" > "$stats"
(while true; do
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' | sed "s/^/$(date +%s),/"
    sleep 2
done) >> "$stats" &
sampler=$!
disown $sampler
trap 'kill $sampler 2>/dev/null || true' EXIT

echo ">> k6 run $script"
k6_status=0
"${compose[@]}" --profile loadtest run --rm --no-deps \
    -e LEVELS -e STEP_SECONDS -e MAX_VUS -e RATE -e DURATION_SECONDS -e MINUTES \
    k6 run --quiet "/scripts/$script.js" || k6_status=$?
kill $sampler 2>/dev/null || true

echo ">> container CPU during the run (100 = one CPU)"
awk -F, 'NR > 1 { gsub("%", "", $3); n[$2]++; s[$2] += $3; if ($3 > m[$2]) m[$2] = $3 }
         END { for (c in n) printf "   %-40s avg %6.1f  max %6.1f\n", c, s[c] / n[c], m[c] }' "$stats" | sort

"${compose[@]}" logs --no-color nginx > "$results/$RUN_LABEL-nginx.log" 2>&1

echo ">> correctness"
"${compose[@]}" exec -T postgres psql -U scheduler -d scheduler -q -f - < loadtest/scripts/verify.sql \
    | tee "$results/$RUN_LABEL-verify.txt"

if grep 'must be 0' "$results/$RUN_LABEL-verify.txt" | grep -qvE '\|\s+0$'; then
    echo ">> CORRECTNESS VIOLATION: see $results/$RUN_LABEL-verify.txt" >&2
    exit 1
fi

echo ">> k6 exit code $k6_status (99 = some phase missed its thresholds)"
if [[ ${STRICT:-0} == 1 ]]; then
    exit "$k6_status"
fi
