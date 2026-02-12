#!/usr/bin/env bash

set -euo pipefail

CONTAINER_NAME="devths-be"
HEALTH_URL="http://localhost:8080/actuator/health"

echo "> Health Check 시작"
echo "> 컨테이너: $CONTAINER_NAME"
echo "> URL: $HEALTH_URL"

# 컨테이너가 실행 중인지 확인
if ! docker ps --filter "name=$CONTAINER_NAME" --format "{{.Names}}" | grep -q "$CONTAINER_NAME"; then
    echo "❌ [Error] 컨테이너가 실행 중이지 않습니다."
    exit 1
fi

# Health Check 대기
sleep 10

for RETRY_COUNT in {1..10}
do
  # Docker 컨테이너 상태 확인
  CONTAINER_STATUS=$(docker inspect -f '{{.State.Status}}' $CONTAINER_NAME 2>/dev/null || echo "not_found")

  if [ "$CONTAINER_STATUS" != "running" ]; then
    echo "> 컨테이너가 실행 중이지 않습니다. 상태: $CONTAINER_STATUS"
  else
    # Health Check API 호출
    RESPONSE=$(curl -s "$HEALTH_URL" || true)
    UP_COUNT=$(echo "${RESPONSE}" | grep -cE '"status"[[:space:]]*:[[:space:]]*"UP"' || echo "0")

    if [ "${UP_COUNT}" -ge 1 ]
    then
        echo "> ✅ Health Check 성공"
        exit 0
    else
        echo "> Health check의 응답을 알 수 없거나 혹은 실행 상태가 아닙니다."
        echo "> Health check: ${RESPONSE}"
    fi
  fi

  if [ "${RETRY_COUNT}" -eq 10 ]
  then
    echo "> ❌ Health Check 실패"

    # 컨테이너 로그 출력 (디버깅용)
    echo "=========== 컨테이너 로그 (마지막 50줄) ==========="
    docker logs --tail 50 $CONTAINER_NAME || true
    echo "================================================="

    # 실패한 컨테이너 정리
    echo "> 🧹 Health Check 실패 컨테이너를 정리합니다."
    docker stop $CONTAINER_NAME || true
    docker rm $CONTAINER_NAME || true

    exit 1
  fi

  echo "> Health check 연결 실패. 재시도... ($RETRY_COUNT/10)"
  sleep 10
done
