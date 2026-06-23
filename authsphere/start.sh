#!/bin/bash
cd /workspaces/Authsphere/authsphere
docker compose up -d
echo "Waiting for postgres..."
sleep 3
mvn spring-boot:run
