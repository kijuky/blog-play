#!/usr/bin/env bash
#MISE description="Build play as GraalVM native-image"

docker compose -f play/compose.graalvm.yaml --env-file=play/.env up --build
