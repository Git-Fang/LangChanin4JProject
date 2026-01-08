#!/bin/bash

echo "=== 应用日志查看工具 ==="
echo ""

# 默认显示所有日志，参数可过滤
if [ $# -eq 0 ]; then
    echo "[1] 查看实时全部日志"
    docker-compose logs -f --tail=100 rag-translation
else
    case "$1" in
        "ai"|"model")
            echo "[2] 过滤AI模型请求/响应日志"
            docker-compose logs -f --tail=200 rag-translation | grep -i "langchain4j\|model\|request\|response\|deepseek\|kimi\|dashscope"
            ;;
        "error")
            echo "[3] 只显示错误日志"
            docker-compose logs --tail=200 rag-translation | grep -i "error\|exception\|failed\|caused"
            ;;
        "mcp")
            echo "[4] 过滤MCP协议日志"
            docker-compose logs -f --tail=100 rag-translation | grep -i "mcp\|baidu"
            ;;
        "web")
            echo "[5] 过滤Web请求日志"
            docker-compose logs -f --tail=100 rag-translation | grep -i "controller\|http\|request"
            ;;
        *)
            echo "使用方法："
            echo "  ./check-logs.sh          # 查看全部实时日志"
            echo "  ./check-logs.sh ai       # 过滤AI模型请求日志"
            echo "  ./check-logs.sh error    # 只显示错误日志"
            echo "  ./check-logs.sh mcp      # 过滤MCP协议日志"
            echo "  ./check-logs.sh web      # 过滤Web请求日志"
            ;;
    esac
fi
