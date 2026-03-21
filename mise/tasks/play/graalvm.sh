#!/usr/bin/env bash
#MISE description="Run play as GraalVM native-image"

docker compose -f play/compose.graalvm.yaml up --build
