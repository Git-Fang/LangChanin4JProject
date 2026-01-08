#!/bin/bash

# 启动脚本

echo "=== RAG增强型翻译系统启动脚本 ==="

# 检查Docker是否安装
if ! command -v docker &> /dev/null
then
    echo "错误：Docker未安装，请先安装Docker"
    exit 1
fi

# 检查Docker Compose是否安装
if ! command -v docker-compose &> /dev/null
then
    echo "错误：Docker Compose未安装，请先安装Docker Compose"
    exit 1
fi

# 检查.env文件是否存在
if [ ! -f .env ]
then
    echo "警告：.env文件不存在，请先创建并配置环境变量"
    echo "请参考README.md中的环境变量配置部分"
    exit 1
fi

echo "开始构建Docker镜像..."
# 使用缓存构建，提高构建速度
docker-compose build

if [ $? -ne 0 ]
then
    echo "错误：Docker镜像构建失败"
    exit 1
fi

echo "Docker镜像构建成功"
echo "开始启动服务..."

# 停止并删除旧容器
docker-compose down

# 启动服务
docker-compose up -d

if [ $? -ne 0 ]
then
    echo "错误：服务启动失败"
    exit 1
fi

echo "服务启动成功"
echo ""
echo "应用访问地址：http://192.168.179.130:8000/index.html"
echo "API文档地址：http://192.168.179.130:8000/doc.html"
echo "MongoDB端口：192.168.179.130:27017"
echo ""
echo "查看所有服务状态：docker-compose ps"
echo "查看应用日志：docker-compose logs -f rag-translation"
echo "查看MongoDB日志：docker-compose logs -f mongo"
echo "停止服务命令：docker-compose down"
echo "=== 启动完成 ==="
