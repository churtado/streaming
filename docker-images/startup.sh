#!/usr/bin/env bash
# connect postgres to the confluent network so that confluent can interact with postgres
# docker network connect <network> <container_name>
docker network connect cpallinone_default postgres_db_1

# connect grafana to influxdb network
docker network connect influxdb grafana

# inspect the ip of the connected container
# docker network inspect <network>
docker network inspect cpallinone_default

