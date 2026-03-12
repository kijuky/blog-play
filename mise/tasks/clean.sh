#!/usr/bin/env bash
#MISE description="Clean cache"

sbt clean
docker system prune -af --volumes
