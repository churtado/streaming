#!/usr/bin/env bash
# docker-compose up | grep connect > /var/log/connect.log
# sudo tail -f /var/log/connect.log
docker start influxdb
docker start festive_chaplygin
docker start grafana
docker start postgres_db_1
cd /home/bodhi/IdeaProjects/streaming/docker-images/flink
docker-compose up

