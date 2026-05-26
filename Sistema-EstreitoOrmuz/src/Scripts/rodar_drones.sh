#!/bin/bash

docker rm -f $(docker ps -aq --filter "name=NoDrone_") 2>/dev/null

echo "Iniciando 8 Drones (IDs 1 a 8)..."

for i in {1..8}
do
   ID_DRONE=$i
   PORTA=$((8080 + i))

   gnome-terminal -- bash -c "docker run --rm --name NoDrone_$ID_DRONE --network host jamileleticia/sistema-estreitoormuz:latest java NoDrone $ID_DRONE $PORTA; exec bash"
done
