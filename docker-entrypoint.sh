#!/bin/bash
# Docker 环境变量修复脚本
# 用于修复 SPRING_DATASOURCE_URL 中的错误参数分隔符

echo "=== Docker 环境变量修复脚本 ==="

# 检查 SPRING_DATASOURCE_URL 环境变量
if [ -n "$SPRING_DATASOURCE_URL" ]; then
    echo "检测到 SPRING_DATASOURCE_URL: $SPRING_DATASOURCE_URL"
    
    # 检查是否包含错误的分隔符
    if echo "$SPRING_DATASOURCE_URL" | grep -q ";"; then
        echo "⚠️  检测到 URL 中包含 ';' 分隔符，正在修复..."
        
        # 修复 URL：将 ; 替换为 &
        FIXED_URL=$(echo "$SPRING_DATASOURCE_URL" | sed 's/;/\&/g')
        export SPRING_DATASOURCE_URL="$FIXED_URL"
        
        echo "✅ URL 已修复: $SPRING_DATASOURCE_URL"
    else
        echo "✅ URL 格式正确"
    fi
else
    echo "未设置 SPRING_DATASOURCE_URL，将使用配置文件中的默认值"
fi

echo ""
echo "=== 启动应用 ==="
echo "SPRING_PROFILES_ACTIVE: $SPRING_PROFILES_ACTIVE"
echo "SPRING_DATASOURCE_URL: $SPRING_DATASOURCE_URL"
echo ""

# 启动应用（保留原有的 JVM 参数配置）
exec java \
    -Dfile.encoding=UTF-8 \
    -Dhttp.proxyHost= \
    -Dhttp.proxyPort= \
    -Dhttps.proxyHost= \
    -Dhttps.proxyPort= \
    -Djava.net.useSystemProxies=false \
    -Dhttp.nonProxyHosts=* \
    -Djdk.httpclient.proxySelector.disableDynamicProxyDiscovery=true \
    -Djdk.httpclient.allowRestrictedHeaders=Connection,Proxy-Authenticate,Proxy-Authorization \
    -Djdk.httpclient.connectionPool.size=20 \
    -Djdk.httpclient.keepAlive.timeout=60 \
    -Djdk.tls.client.protocols=TLSv1.2,TLSv1.3 \
    -Djdk.tls.client.cipherSuites=TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 \
    -jar app.jar "$@"
