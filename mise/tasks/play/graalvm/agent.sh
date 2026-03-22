#!/usr/bin/env bash
#MISE description="Run play as GraalVM native-image-agent"

docker compose -f play/compose.graalvm.agent.yaml --env-file=play/.env up --build
