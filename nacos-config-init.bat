@echo off
REM ============================================================================
REM Nacos配置初始化脚本
REM 用于在Nacos中创建项目的配置文件
REM ============================================================================

set NACOS_SERVER=%NACOS_SERVER_ADDR%
if "%NACOS_SERVER%"=="" set NACOS_SERVER=localhost:8848

echo ============================================
echo   Nacos配置初始化
echo ============================================
echo.
echo   Nacos服务器: %NACOS_SERVER%
echo.

REM 读取配置文件内容
set DATA_ID_DOCKER=RAGTranslationApplication-docker.yml
set DATA_ID_STANDALONE=RAGTranslationApplication-standalone.yml
set GROUP=DEFAULT_GROUP

echo [1/4] 创建Docker环境配置...
curl -X POST "http://%NACOS_SERVER%/nacos/v1/cs/configs" ^
    -d "dataId=%DATA_ID_DOCKER%" ^
    -d "group=%GROUP%" ^
    -d "type=yaml" ^
    -d "content=server:
  port: 8000
  address: 0.0.0.0

spring:
  application:
    name: RAGTranslationApplication
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://mysql:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true
    username: root
    password: root
  data:
    mongodb:
      uri: mongodb://mongo:27017/chat_db
    redis:
      host: redis
      port: 6379
  kafka:
    bootstrap-servers: kafka:9092

ai:
  deepSeek:
    base-url: https://api.deepseek.com/v1
    model: deepseek-chat
    apiKey: ${DeepSeek_API_KEY:}
  kimi:
    base-url: https://api.moonshot.cn/v1
    model: kimi-k2-turbo-preview
    apiKey: ${KIMI_API_KEY:}
  embeddingStore:
    qdrant:
      collectionName: ragTranslation-1226
      host: qdrant
      port: 6334
  dashscope:
    apiKey: ${DASHSCOPE_API_KEY:}
    model: qwen-max
"
echo.
echo   Docker配置创建完成
echo.

echo [2/4] 创建Standalone环境配置...
curl -X POST "http://%NACOS_SERVER%/nacos/v1/cs/configs" ^
    -d "dataId=%DATA_ID_STANDALONE%" ^
    -d "group=%GROUP%" ^
    -d "type=yaml" ^
    -d "content=server:
  port: 8020

spring:
  application:
    name: RAGTranslationApplication
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=UTC^&allowPublicKeyRetrieval=true^&useSSL=false
    username: root
    password: root
  data:
    mongodb:
      uri: mongodb://localhost:27017/chat_db
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092

ai:
  deepSeek:
    base-url: https://api.deepseek.com/v1
    model: deepseek-chat
    apiKey: ${DeepSeek_API_KEY:}
  kimi:
    base-url: https://api.moonshot.cn/v1
    model: kimi-k2-turbo-preview
    apiKey: ${KIMI_API_KEY:}
  embeddingStore:
    qdrant:
      collectionName: ragTranslation-1226
      host: localhost
      port: 6334
  dashscope:
    apiKey: ${DASHSCOPE_API_KEY:}
    model: qwen-vl-max
"
echo.
echo   Standalone配置创建完成
echo.

echo [3/4] 创建通用配置(可选)...
curl -X POST "http://%NACOS_SERVER%/nacos/v1/cs/configs" ^
    -d "dataId=common.yml" ^
    -d "group=%GROUP%" ^
    -d "type=yaml" ^
    -d "content=# 通用配置
logging:
  level:
    root: INFO
    org.fb: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
"
echo.
echo   通用配置创建完成
echo.

echo [4/4] 验证配置是否创建成功...
echo.
echo   Docker配置: http://%NACOS_SERVER%/nacos/#/configurationManagement?dataId=%DATA_ID_DOCKER%^&group=%GROUP%
echo   Standalone配置: http://%NACOS_SERVER%/nacos/#/configurationManagement?dataId=%DATA_ID_STANDALONE%^&group=%GROUP%
echo   通用配置: http://%NACOS_SERVER%/nacos/#/configurationManagement?dataId=common.yml^&group=%GROUP%
echo.

echo ============================================
echo   Nacos配置初始化完成!
echo ============================================
echo.
echo   使用说明:
echo   1. 访问 http://%NACOS_SERVER%/nacos 查看配置
echo   2. 默认用户名: nacos, 密码: nacos
echo   3. 修改配置后保存，应用程序会自动刷新配置
echo   4. 支持动态配置更新，无需重启服务
echo.
echo ============================================
pause
