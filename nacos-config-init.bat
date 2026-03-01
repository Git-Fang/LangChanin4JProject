@echo off
REM ============================================================================
REM Nacos Config Initialization Script
REM Creates project configuration files in Nacos
REM ============================================================================

setlocal

set NACOS_SERVER=%NACOS_SERVER_ADDR%
if "%NACOS_SERVER%"=="" set NACOS_SERVER=localhost:8848

echo ============================================
echo   Nacos Config Initialization
echo ============================================
echo.
echo   Nacos Server: %NACOS_SERVER%
echo.

set DATA_ID_DOCKER=RAGTranslationApplication-docker.yml
set DATA_ID_STANDALONE=RAGTranslationApplication-standalone.yml
set GROUP=DEFAULT_GROUP

set TEMP_DIR=%TEMP%\nacos_config_init
mkdir "%TEMP_DIR%" 2>nul

REM ========================================================================
REM 1. Create Docker Environment Config
REM ========================================================================
echo [1/4] Creating Docker environment config...

(
echo server:
echo   port: 8000
echo   address: 0.0.0.0
echo.
echo spring:
echo   application:
echo     name: RAGTranslationApplication
echo   datasource:
echo     driver-class-name: com.mysql.cj.jdbc.Driver
echo     url: "jdbc:mysql://mysql:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true"
echo     username: root
echo     password: root
echo   data:
echo     mongodb:
echo       uri: mongodb://mongo:27017/chat_db
echo     redis:
echo       host: redis
echo       port: 6379
echo   kafka:
echo     bootstrap-servers: kafka:9092
echo.
echo ai:
echo   deepSeek:
echo     base-url: https://api.deepseek.com/v1
echo     model: deepseek-chat
echo     apiKey: ${DeepSeek_API_KEY:}
echo   kimi:
echo     base-url: https://api.moonshot.cn/v1
echo     model: kimi-k2-turbo-preview
echo     apiKey: ${KIMI_API_KEY:}
echo   embeddingStore:
echo     qdrant:
echo       collectionName: ragTranslation-1226
echo       host: qdrant
echo       port: 6334
echo   dashscope:
echo     apiKey: ${DASHSCOPE_API_KEY:}
echo     model: qwen-max
) > "%TEMP_DIR%\docker.yml"

curl -s -X POST "http://%NACOS_SERVER%/nacos/v1/cs/configs" -d "dataId=%DATA_ID_DOCKER%" -d "group=%GROUP%" -d "type=yaml" --data-binary @"%TEMP_DIR%\docker.yml"
if %errorlevel% equ 0 (echo   [OK] Docker config created) else (echo   [ERROR] Docker config failed)
echo.

REM ========================================================================
REM 2. Create Standalone Environment Config
REM ========================================================================
echo [2/4] Creating Standalone environment config...

(
echo server:
echo   port: 8020
echo.
echo spring:
echo   application:
echo     name: RAGTranslationApplication
echo   datasource:
echo     driver-class-name: com.mysql.cj.jdbc.Driver
echo     url: "jdbc:mysql://localhost:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=UTC^&allowPublicKeyRetrieval=true^&useSSL=false"
echo     username: root
echo     password: root
echo   data:
echo     mongodb:
echo       uri: mongodb://localhost:27017/chat_db
echo     redis:
echo       host: localhost
echo       port: 6379
echo   kafka:
echo     bootstrap-servers: localhost:9092
echo.
echo ai:
echo   deepSeek:
echo     base-url: https://api.deepseek.com/v1
echo     model: deepseek-chat
echo     apiKey: ${DeepSeek_API_KEY:}
echo   kimi:
echo     base-url: https://api.moonshot.cn/v1
echo     model: kimi-k2-turbo-preview
echo     apiKey: ${KIMI_API_KEY:}
echo   embeddingStore:
echo     qdrant:
echo       collectionName: ragTranslation-1226
echo       host: localhost
echo       port: 6334
echo   dashscope:
echo     apiKey: ${DASHSCOPE_API_KEY:}
echo     model: qwen-vl-max
) > "%TEMP_DIR%\standalone.yml"

curl -s -X POST "http://%NACOS_SERVER%/nacos/v1/cs/configs" -d "dataId=%DATA_ID_STANDALONE%" -d "group=%GROUP%" -d "type=yaml" --data-binary @"%TEMP_DIR%\standalone.yml"
if %errorlevel% equ 0 (echo   [OK] Standalone config created) else (echo   [ERROR] Standalone config failed)
echo.

REM ========================================================================
REM 3. Create Common Config
REM ========================================================================
echo [3/4] Creating common config...

(
echo # Common config
echo logging:
echo   level:
echo     root: INFO
echo     org.fb: DEBUG
echo.
echo management:
echo   endpoints:
echo     web:
echo       exposure:
echo         include: health,info,metrics,prometheus
) > "%TEMP_DIR%\common.yml"

curl -s -X POST "http://%NACOS_SERVER%/nacos/v1/cs/configs" -d "dataId=common.yml" -d "group=%GROUP%" -d "type=yaml" --data-binary @"%TEMP_DIR%\common.yml"
if %errorlevel% equ 0 (echo   [OK] Common config created) else (echo   [ERROR] Common config failed)
echo.

REM ========================================================================
REM 4. Display Links
REM ========================================================================
echo [4/4] Verifying configs...
echo.
echo   Docker Config:       http://%NACOS_SERVER%/nacos/#/configurationManagement?dataId=%DATA_ID_DOCKER%^&group=%GROUP%
echo   Standalone Config:   http://%NACOS_SERVER%/nacos/#/configurationManagement?dataId=%DATA_ID_STANDALONE%^&group=%GROUP%
echo   Common Config:       http://%NACOS_SERVER%/nacos/#/configurationManagement?dataId=common.yml^&group=%GROUP%
echo.

REM Cleanup
del /q "%TEMP_DIR%\*.yml" 2>nul
rmdir "%TEMP_DIR%" 2>nul

endlocal

echo ============================================
echo   Nacos Config Initialization Complete!
echo ============================================
echo.
echo   Usage:
echo   1. Open http://%NACOS_SERVER%/nacos
echo   2. Login: nacos/nacos
echo   3. Modify configs as needed
echo   4. Config changes auto-refresh, no restart needed
echo.
echo ============================================
pause
