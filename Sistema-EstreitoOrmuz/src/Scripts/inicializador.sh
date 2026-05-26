#!/bin/bash
IMG="jamileleticia/sistema-estreitoormuz:latest"

docker run --rm -it --network host --name "${1}_${2}" $IMG java $@
