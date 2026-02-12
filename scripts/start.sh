#!/usr/bin/env bash

set -euo pipefail

REPOSITORY=/home/ubuntu/app
cd $REPOSITORY

echo "> 배포 시작"

# 1. image-info.env 파일에서 이미지 정보 로드
if [ ! -f "$REPOSITORY/image-info.env" ]; then
    echo "❌ [Error] image-info.env 파일을 찾을 수 없습니다."
    exit 1
fi

source $REPOSITORY/image-info.env

# 환경 변수 export (docker-compose에서 사용)
export FULL_IMAGE
export ECR_REGISTRY
export ECR_REPOSITORY
export IMAGE_TAG

echo "> ECR 이미지: $FULL_IMAGE"

# 2. ECR 로그인
echo "> ECR 로그인"
AWS_REGION=${AWS_REGION:-ap-northeast-2}
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# 3. 이미지 Pull
echo "> Docker 이미지 Pull"
docker pull "$FULL_IMAGE"

# 4. 기존 컨테이너 정리 (있다면)
echo "> 기존 컨테이너 정리"
if docker ps -a --filter "name=devths-be" --format "{{.Names}}" | grep -q "devths-be"; then
    docker stop devths-be || true
    docker rm devths-be || true
fi

# 5. docker-compose로 컨테이너 실행
echo "> Docker Compose로 애플리케이션 시작"
if [ ! -f "$REPOSITORY/docker-compose.yml" ]; then
    echo "❌ [Error] docker-compose.yml 파일을 찾을 수 없습니다."
    exit 1
fi

# docker-compose 실행 (detached mode)
docker-compose up -d

echo "> 배포 완료. Health Check 대기 중..."
