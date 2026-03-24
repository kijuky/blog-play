#!/usr/bin/env bash
#MISE description="Build play prod-mode"

sbt play/Docker/stage
docker compose -f play/compose.yaml --env-file=play/.env up --build
