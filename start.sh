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
# 强制拉取最新镜像，确保不使用缓存的旧配置
docker-compose build --no-cache --pull

if [ $? -ne 0 ]
then
    echo "错误：Docker镜像构建失败"
    exit 1
fi

echo "Docker镜像构建成功"
echo "开始启动服务..."

docker-compose up -d

if [ $? -ne 0 ]
then
    echo "错误：服务启动失败"
    exit 1
fi

echo "服务启动成功"
echo "应用访问地址：http://172.21.192.1:8000"
echo "API文档地址：http://172.21.192.1:8000/doc.html"
echo "=== 启动完成 ==="
