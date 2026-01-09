#!/bin/bash

# RAG Translation System - 管理脚本

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 帮助信息
usage() {
    echo "使用方法: $0 [命令]"
    echo
    echo "可用命令："
    echo "  start    - 启动服务（skip tasks if already running)"
    echo "  stop     - 停止服务"
    echo "  restart  - 重启服务"
    echo "  logs     - 查看实时日志"
    echo "  status   - 检查服务状态"
    echo "  health   - 健康检查"
    echo "  clean    - 清理所有容器和数据卷"
    echo "  update   - 重新构建并启动服务"
    echo "  shell    - 进入应用容器命令行"
    echo "  backup   - 备份数据卷"
}

# 检查Docker
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}错误：Docker未运行！请先启动Docker Desktop${NC}"
    exit 1
fi

# 定义compose文件路径
COMPOSE_FILE="docker-compose.desktop.yml"

# 检查compose文件
if [ ! -f "$COMPOSE_FILE" ]; then
    echo -e "${RED}错误：找到 $COMPOSE_FILE 文件！请确保在当前目录${NC}"
    exit 1
fi

# 启动服务
start_service() {
    echo -e "${YELLOW}正在启动服务...${NC}"
    docker-compose -f $COMPOSE_FILE up -d
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}服务启动成功！${NC}"
        echo "等待服务初始化..."
        sleep 5
        check_health
    else
        echo -e "${RED}服务启动失败！${NC}"
        exit 1
    fi
}

# 停止服务
stop_service() {
    echo -e "${YELLOW}正在停止服务...${NC}"
    docker-compose -f $COMPOSE_FILE down
    echo -e "${GREEN}服务已停止${NC}"
}

# 重启服务
restart_service() {
    echo -e "${YELLOW}正在重启服务...${NC}"
    docker-compose -f $COMPOSE_FILE restart
    echo "等待服务重启..."
    sleep 8
    check_health
}

# 查看日志
show_logs() {
    if [ "$2" == "-f" ]; then
        docker-compose -f $COMPOSE_FILE logs -f
    else
        docker-compose -f $COMPOSE_FILE logs --tail=50
    fi
}

# 检查状态
show_status() {
    echo -e "${GREEN}=== 服务状态 ===${NC}"
    docker-compose -f $COMPOSE_FILE ps
    echo

    # 显示容器统计
    echo -e "${GREEN}=== 资源使用统计 ===${NC}"
    docker stats --no-stream --all --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}"
}

# 健康检查
check_health() {
    echo -e "${YELLOW}执行健康检查...${NC}"

    # 检查健康端点
    health_status=$(docker exec rag-translation curl -s http://localhost:8000/actuator/health 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

    if [ "$health_status" == "UP" ]; then
        echo -e "${GREEN}应用健康状态：UP ✓${NC}"
    else
        echo -e "${RED}应用健康状态：DOWN ✗${NC}"
        echo -e "${YELLOW}检查详细日志：${NC}"
        docker-compose -f $COMPOSE_FILE logs --tail=10 rag-translation
    fi

    # 检查数据库连接
    for service in mysql mongo redis qdrant; do
        if docker exec rag-translation nc -zv $service $(echo $service | awk '{if($0=="mysql")print 3306;if($0=="mongo")print 27017;if($0=="redis")print 6379;if($0=="qdrant")print 6333}') 2>/dev/null; then
            echo -e "${GREEN}$service 连接正常 ✓${NC}"
        else
            echo -e "${RED}$service 连接异常 ✗${NC}"
        fi
    done
}

# 清理环境
clean_all() {
    echo -e "${YELLOW}=== 警告：此操作将删除所有数据和容器！===${NC}"
    read -p "确定要继续吗？ (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${RED}正在清理所有资源...${NC}"
        docker-compose -f $COMPOSE_FILE down -v --remove-orphans
        docker system prune -f
        echo -e "${GREEN}清理完成${NC}"
    else
        echo "操作已取消"
    fi
}

# 进入容器shell
goto_shell() {
    docker exec -it rag-translation /bin/bash
}

# 备份数据
backup_data() {
    BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
    mkdir -p $BACKUP_DIR
    echo -e "${YELLOW}正在备份数据到 $BACKUP_DIR...${NC}"

    # 导出MySQL
    docker exec rag-mysql mysqldump -uroot -proot rag_translation > "$BACKUP_DIR/mysql.dump.sql"

    # 导出MongoDB
    docker exec rag-mongo mongodump --db chat_db --archive=/tmp/mongo.archive || true
    docker cp rag-mongo:/tmp/mongo.archive "$BACKUP_DIR/mongo.dump.archive"

    # 备份Redis（如果使用了RDB持久化）
    # docker cp rag-redis:/data/dump.rdb "$BACKUP_DIR/redis.dump.rdb" || true

    # 备份Qdrant
    docker run --rm -v rag-translation_qdrant-data:/data -v $(pwd)/$BACKUP_DIR:/backup alpine tar czf /backup/qdrant-data.tar.gz -C /data .

    echo -e "${GREEN}备份完成！${NC}"
}

# 主逻辑
case "$1" in
    start)
        start_service
        ;;
    stop)
        stop_service
        ;;
    restart)
        restart_service
        ;;
    logs)
        show_logs "$@"
        ;;
    status)
        show_status
        ;;
    health)
        check_health
        ;;
    clean)
        clean_all
        ;;
    update)
        echo -e "${YELLOW}正在重新构建和启动...${NC}"
        docker-compose -f $COMPOSE_FILE build --no-cache
        docker-compose -f $COMPOSE_FILE up -d
        ;;
    shell)
        goto_shell
        ;;
    backup)
        backup_data
        ;;
    *)
        usage
        ;;
esac