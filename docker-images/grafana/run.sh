# If it's the first time
docker run -d --name=grafana -p 3000:3000 grafana/grafana

# If the container's been saved
docker start grafana
