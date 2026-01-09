#!/bin/bash

# RAG Translation System - Docker Desktop部署脚本（Linux/MacOS）

echo "=== RAG翻译系统 Docker Desktop 部署脚本 ==="
echo

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "错误：Docker未运行！请先启动Docker Desktop"
    exit 1
fi

# 检查docker-compose可用性
if ! command -v docker-compose &> /dev/null; then
    echo "错误：Docker Compose未安装！"
    exit 1
fi

# 检查.env文件
if [ ! -f ".env" ]; then
    echo "警告：未找到.env文件，将使用默认配置"
    echo
fi

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 清理旧容器
echo -e "${YELLOW}正在清理旧容器...${NC}"
docker-compose -f docker-compose.desktop.yml down

# 构建镜像
echo -e "${YELLOW}正在构建Docker镜像...${NC}"
docker-compose -f docker-compose.desktop.yml build --no-cache

if [ $? -ne 0 ]; then
    echo -e "${RED}错误：Docker镜像构建失败！${NC}"
    exit 1
fi

# 启动服务
echo -e "${YELLOW}正在启动服务...${NC}"
docker-compose -f docker-compose.desktop.yml up -d

if [ $? -ne 0 ]; then
    echo -e "${RED}错误：服务启动失败！${NC}"
    exit 1
fi

# 等待服务启动
echo "等待服务完全启动..."
sleep 10

# 显示服务状态
echo -e "${GREEN}=== 部署成功！${NC}"
echo

# 显示访问地址和信息
echo -e "${GREEN}访问地址：${NC}"
echo "  应用界面：    http://localhost:8000/index.html"
echo "  API文档：     http://localhost:8000/doc.html"
echo "  健康检查：    http://localhost:8000/actuator/health"
echo

echo -e "${GREEN}服务端口：${NC}"
echo "  应用端口：    localhost:8000"
echo "  MySQL：       localhost:3306"
echo "  MongoDB：     localhost:27017"
echo "  Redis：       localhost:6379"
echo "  Qdrant：      localhost:6333"
echo "  Ollama：      localhost:11434"
echo

echo -e "${GREEN}常用命令：${NC}"
echo "  查看实时日志：    docker-compose -f docker-compose.desktop.yml logs -f"
echo "  停止服务：        docker-compose -f docker-compose.desktop.yml down"
echo "  查看状态：        docker-compose -f docker-compose.desktop.yml ps"
echo "  重启应用：        docker-compose -f docker-compose.desktop.yml restart rag-translation"
echo

# 检查服务是否健康
echo -e "${YELLOW}检查服务状态...${NC}"
docker-compose -f docker-compose.desktop.yml ps

# 显示最后几行日志
echo
echo -e "${YELLOW}最近的应用日志：${NC}"
docker-compose -f docker-compose.desktop.yml logs --tail=10 rag-translation