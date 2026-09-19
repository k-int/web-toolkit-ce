#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_dir"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 21}"
run_id="toolkit-proof-$$"
pg="$run_id-pg"
s3="$run_id-s3"
created_pg=false
created_s3=false
cleanup() {
  status=$?
  trap - EXIT
  if "$created_s3"; then podman rm -f -v "$s3" >/dev/null || status=1; fi
  if "$created_pg"; then podman rm -f -v "$pg" >/dev/null || status=1; fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
podman create --name "$pg" -p 127.0.0.1::5432 -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=test --tmpfs /var/lib/postgresql/data docker.io/library/postgres:17-alpine >/dev/null
created_pg=true
podman start "$pg" >/dev/null
pg_port=$(podman port "$pg" 5432/tcp | sed -n 's/^127\.0\.0\.1://p')
for n in {1..30}; do podman exec "$pg" pg_isready -U test -d test >/dev/null && break; sleep 1; done
podman exec "$pg" psql -U test -d test -c "CREATE SCHEMA test" >/dev/null
podman create --name "$s3" -p 127.0.0.1::9000 -e MINIO_ROOT_USER=DIKU_AGG_ACCESS_KEY -e MINIO_ROOT_PASSWORD=DIKU_AGG_SECRET_KEY --entrypoint minio --tmpfs /data docker.libsdev.k-int.com/knowledgeintegration/cicd-minio-folio:v2 server /data >/dev/null
created_s3=true
podman start "$s3" >/dev/null
s3_port=$(podman port "$s3" 9000/tcp | sed -n 's/^127\.0\.0\.1://p')
for n in {1..30}; do curl -fsS "http://127.0.0.1:$s3_port/minio/health/ready" >/dev/null 2>&1 && break; sleep 1; done
podman exec "$s3" mc alias set proof http://127.0.0.1:9000 DIKU_AGG_ACCESS_KEY DIKU_AGG_SECRET_KEY >/dev/null
podman exec "$s3" mc mb proof/diku-shared >/dev/null
env -i HOME="$HOME" USER="$(id -un)" LANG=C.UTF-8 PATH="$JAVA_HOME/bin:$PATH" JAVA_HOME="$JAVA_HOME" \
  TOOLKIT_TEST_JDBC_URL="jdbc:postgresql://127.0.0.1:$pg_port/test" \
  TOOLKIT_TEST_S3_ENDPOINT="http://127.0.0.1:$s3_port" ./gradlew --no-daemon --no-parallel -Dgrails.env=test-livedb -I scripts/integration-test.init.gradle integrationTest --rerun-tasks "$@"
