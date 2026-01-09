#!/bin/bash

echo "=== RAG Translation System - Docker Desktop 构建测试 ==="
echo

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查Docker
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}错误：Docker Desktop 未运行！请先启动 Docker Desktop${NC}"
    exit 1
fi

echo -e "${YELLOW}[1/5] 验证 Docker Compose 配置...${NC}"
docker-compose -f docker-compose.desktop.yml config > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Docker Compose 配置有效${NC}"
else
    echo -e "${RED}✗ Docker Compose 配置有错误${NC}"
    exit 1
fi

echo -e "${YELLOW}[2/5] 测试构建应用镜像...${NC}"
# 仅构建应用镜像以测试
docker build -f Dockerfile.desktop -t rag-translation:test .

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 应用镜像构建成功${NC}"
else
    echo -e "${RED}✗ 应用镜像构建失败${NC}"
    echo -e "${YELLOW}请检查构建日志并修复问题${NC}"
    exit 1
fi

echo -e "${YELLOW}[3/5] 验证镜像...${NC}"
docker images rag-translation:test --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"

echo -e "${YELLOW}[4/5] 测试镜像启动...${NC}"
# 测试镜像是否可以正常启动
docker run -d --name test-rag-translation \
  -p 10800:8000 \
  -e DeepSeek_API_KEY=test-key \
  -e KIMI_API_KEY=test-key \
  -e DASHSCOPE_API_KEY=test-key \
  rag-translation:test

# 等待启动
sleep 10

# 检查状态
if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -q "test-rag-translation"; then
    status=$(docker inspect --format='{{.State.Health.Status}}' test-rag-translation 2>/dev/null || echo "unknown")
    echo -e "${GREEN}✓ 容器启动成功，健康状态: $status${NC}"

    # 测试端口
    curl -f http://localhost:10800/actuator/health > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 应用服务响应正常${NC}"
    else
        echo -e "${YELLOW}! 应用服务端口未响应（测试环境无数据库连接）${NC}"
    fi
else
    echo -e "${RED}✗ 容器启动失败${NC}"
    docker logs test-rag-translation --tail=20
fi

echo -e "${YELLOW}[5/5] 清理测试容器...${NC}"
docker stop test-rag-translation > /dev/null 2>&1
docker rm test-rag-translation > /dev/null 2>&1
docker rmi rag-translation:test > /dev/null 2>&1

echo
echo -e "${GREEN}=== 构建测试完成 ===${NC}"
echo
echo "下一步："
echo "1. 配置有效的 API Keys 到 .env 文件"
echo "2. 运行部署脚本："
echo "   - Linux/MacOS: ./deploy-desktop.sh"
echo "   - Windows: deploy-desktop.bat"
echo
echo "注意：由于需要下载各种基础镜像，首次部署可能需要较长时间"
echo "建议保持网络畅通，耐心等待下载完成"""""""'""'"'' ""'""