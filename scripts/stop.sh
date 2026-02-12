#!/usr/bin/env bash

set -euo pipefail

REPOSITORY=/home/ubuntu/app
CONTAINER_NAME="devths-be"

echo "> 기존 컨테이너 정리"

cd $REPOSITORY

# Docker Compose로 내리기 (docker-compose.yml이 있다면)
if [ -f "$REPOSITORY/docker-compose.yml" ]; then
    echo "> Docker Compose로 컨테이너 중지"
    docker-compose down || true
fi

# 개별 컨테이너 정리 (혹시 남아있다면)
if docker ps -a --filter "name=$CONTAINER_NAME" --format "{{.Names}}" | grep -q "$CONTAINER_NAME"; then
    echo "> 컨테이너 '$CONTAINER_NAME' 중지 및 제거"
    docker stop $CONTAINER_NAME || true
    docker rm $CONTAINER_NAME || true
else
    echo "> 정리할 컨테이너가 없습니다."
fi

echo "> 컨테이너 정리 완료"
