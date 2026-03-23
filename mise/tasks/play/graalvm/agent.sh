#!/usr/bin/env bash
#MISE description="Run tracing agent"

docker compose -f play/compose.graalvm.agent.yaml --env-file=play/.env up --build
