@echo off
cd /d F:\firedemo\edumind
docker compose up prometheus grafana -d --no-deps
start http://localhost:3001
