#!/usr/bin/env bash
cd /home/bodhi/IdeaProjects/streaming/docker-images/confluent/cp-docker-images/examples/cp-all-in-one
docker-compose up | grep connect > /var/log/connect.log &
docker start influxdb
docker start festive_chaplygin
cd /home/bodhi/IdeaProjects/streaming/docker-images/flink
docker compose up
docker start grafana
docker start postgres_db_1

