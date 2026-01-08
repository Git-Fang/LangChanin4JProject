#!/bin/bash

# Ubuntu虚拟机部署脚本

echo "=== RAG翻译系统 Docker部署脚本 ==="

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "错误：Docker未安装"
    echo "安装Docker命令："
    echo "curl -fsSL https://get.docker.com | sh"
    exit 1
fi

# 检查Docker Compose是否安装
if ! command -v docker-compose &> /dev/null; then
    echo "错误：Docker Compose未安装"
    echo "安装Docker Compose命令："
    echo "sudo apt-get update && sudo apt-get install -y docker-compose"
    exit 1
fi

# 检查.env文件
if [ ! -f .env ]; then
    echo "警告：.env文件不存在，请先配置环境变量"
    exit 1
fi

echo "开始构建Docker镜像..."
docker-compose build

if [ $? -ne 0 ]; then
    echo "错误：Docker镜像构建失败"
    exit 1
fi

echo "Docker镜像构建成功"
echo "开始启动服务..."

# 停止并删除旧容器
docker-compose down

# 启动服务
docker-compose up -d

if [ $? -ne 0 ]; then
    echo "错误：服务启动失败"
    exit 1
fi

echo ""
echo "=== 服务启动成功 ==="
echo "应用访问地址：http://192.168.179.130:8000/index.html"
echo "API文档地址：http://192.168.179.130:8000/doc.html"
echo "MongoDB端口：192.168.179.130:27017"
echo ""
echo "查看所有服务状态：docker-compose ps"
echo "查看应用日志：docker-compose logs -f rag-translation"
echo "查看MongoDB日志：docker-compose logs -f mongo"
echo "停止服务命令：docker-compose down"
echo "==========================="
