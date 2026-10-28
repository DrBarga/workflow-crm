#!/usr/bin/env sh
set -eu

docker compose --env-file .env.example --profile tools run --rm migration-tools \
  mvn --batch-mode --no-transfer-progress liquibase:rollback -Dliquibase.rollbackCount=1
