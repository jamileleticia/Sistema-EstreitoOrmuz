#!/bin/bash

docker rm -f $(docker ps -aq --filter "name=NoSensor_") 2>/dev/null

echo "Iniciando 8 Sensores (IDs 1 a 8)..."

for i in {1..8}
do
   ID_SENSOR=$i
   # Distribui os sensores entre os 4 brokers
   ID_BROKER=$(( (i-1) % 4 + 1 ))
   PORTA_BROKER=$((7070 + ID_BROKER))

   gnome-terminal -- bash -c "docker run --rm --name NoSensor_$ID_SENSOR --network host jamileleticia/sistema-estreitoormuz:latest java NoSensor $ID_SENSOR $ID_BROKER $PORTA_BROKER; exec bash"
done
