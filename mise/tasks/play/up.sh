#!/usr/bin/env bash
#MISE description="Run play prod-mode"

sbt play/Docker/stage && docker compose -f play/compose.yaml up --build
