#!/bin/bash
# RAG Translation System - Docker Desktop部署脚本（Unix/Linux/macOS）
# 用于将项目快速部署到Docker Desktop并支持外部访问

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印函数
print_header() {
    echo -e "${BLUE}=======================================================${NC}"
    echo -e "${BLUE}        RAG 翻译系统 - Docker Desktop 部署工具${NC}"
    echo -e "${BLUE}=======================================================${NC}"
    echo
}

print_error() {
    echo -e "${RED}[错误] $1${NC}"
}

print_info() {
    echo -e "${GREEN}[信息] $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}[警告] $1${NC}"
}

print_separator() {
    echo -e "${BLUE}-------------------------------------------------------${NC}"
}

# 检查Docker是否运行
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        print_error "Docker Desktop未运行！请先启动Docker Desktop"
        exit 1
    fi
}

# 检查Docker Compose
check_docker_compose() {
    if ! docker compose version > /dev/null 2>&1; then
        print_error "Docker Compose未安装或不可用！"
        exit 1
    fi
}

# 检查.env文件
check_env_file() {
    if [ ! -f ".env" ]; then
        print_warning "未找到.env文件，将使用默认配置"
        print_warning "如需配置API密钥，请复制 .env.example 为 .env"
    fi
}

# 清理旧容器
cleanup() {
    print_info "正在清理旧容器..."
    print_separator
    docker compose down --remove-orphans --volumes || true
}

# 构建镜像
build_images() {
    print_info "正在构建Docker镜像..."
    print_separator
    docker compose build --no-cache
    if [ $? -ne 0 ]; then
        print_error "Docker镜像构建失败！"
        exit 1
    fi
}

# 启动服务
start_services() {
    print_info "正在启动服务..."
    print_separator
    docker compose up -d
    if [ $? -ne 0 ]; then
        print_error "服务启动失败！"
        exit 1
    fi
}

# 等待服务就绪
wait_for_services() {
    print_info "等待服务完全启动..."
    print_separator

    local timeout=60
    local count=0

    # 等待容器状态为healthy
    while [ $count -lt $timeout ]; do
        if docker compose ps rag-translation | grep -q "healthy"; then
            print_info "应用服务已就绪！"
            return 0
        fi
        count=$((count + 2))
        echo "[等待] 服务启动中... ($count/$timeout)"
        sleep 2
    done

    print_warning "服务启动超时，请检查日志"
}

# 显示服务状态
show_status() {
    echo
    print_info "当前运行状态："
    docker compose ps
}

# 主函数
main() {
    print_header

    # 执行检查
    check_docker
    check_docker_compose
    check_env_file

    # 询问确认
    echo
    read -p "是否要继续部署？(y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "部署已取消"
        exit 0
    fi

    # 执行部署步骤
    cleanup
    build_images
    start_services
    wait_for_services
    show_status

    # 输出成功信息
    echo
    echo -e "${GREEN}=======================================================${NC}"
    echo -e "${GREEN}                    部署成功！${NC}"
    echo -e "${GREEN}=======================================================${NC}"
    echo
    echo -e "${BLUE}>>= 应用访问地址 =================${NC}"
    echo -e "    Web界面:  ${GREEN}http://localhost:8000/index.html${NC}"
    echo -e "    API文档:  ${GREEN}http://localhost:8000/doc.html${NC}"
    echo
    echo -e "${BLUE}>>= 管理界面 ====================${NC}"
    echo -e "    RabbitMQ管理界面: ${GREEN}http://localhost:15672 (admin/admin123)${NC}"
    echo -e "    RedisInsight:     ${GREEN}http://localhost:8001${NC}"
    echo
    echo -e "${BLUE}>>= 服务端口列表 ===============${NC}"
    echo -e "    应用服务:   ${GREEN}localhost:8000${NC}"
    echo -e "    MySQL:      ${GREEN}localhost:3306${NC}"
    echo -e "    MongoDB:    ${GREEN}localhost:27017${NC}"
    echo -e "    Redis:      ${GREEN}localhost:6379${NC}"
    echo -e "    RabbitMQ:   ${GREEN}localhost:5672${NC}"
    echo -e "    RabbitMQ管理: ${GREEN}localhost:15672${NC}"
    echo -e "    Qdrant:     ${GREEN}localhost:6333${NC}"
    echo
    echo -e "${BLUE}>>= 管理命令 =====================${NC}"
    echo -e "    查看日志:   ${GREEN}docker compose logs -f${NC}"
    echo -e "    停止服务:   ${GREEN}docker compose down${NC}"
    echo -e "    查看状态:   ${GREEN}docker compose ps${NC}"
    echo -e "    重启服务:   ${GREEN}docker compose restart${NC}"
    echo -e "    停止并清理: ${GREEN}docker compose down --volumes${NC}"
    echo
    echo -e "${GREEN}=======================================================${NC}"
    echo
}

# 运行主函数
main

exit 0