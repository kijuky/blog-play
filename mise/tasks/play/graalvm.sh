#!/usr/bin/env bash
#MISE description="Run play dev-mode"

sbt play/Docker/stage && docker compose -f play/compose.graalvm.yml up --build
