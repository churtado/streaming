# create a network for influxdb and chronograf
docker network create influxdb

# generate default configuration file
docker run --rm influxdb influxd config > influxdb.conf

# run influx container with the network so that it can communicate with chronograf
# make sure the config is mounted to the container
# start the container with the network
docker run -d -p 8086:8086 --name=influxdb --net=influxdb influxdb

# run chronograf detached on the same network
docker run -d -p 8888:8888 --net=influxdb -v chronograf:/var/lib/chronograf chronograf --influxdb-url=http://influxdb:8086

# connect postgres to the confluent network so that confluent can interact with postgres
# docker network connect <network> <container_name>
docker network connect cpallinone_default postgres_db_1

# inspect the ip of the connected container
# docker network inspect <network>
docker network inspect cpallinone_default