@echo off
echo =========================================
echo   本地知识库检索测试
echo =========================================
echo.

echo [测试1] 检查知识库目录结构
echo ----------------------------------------
call mvn exec:java -Dexec.mainClass="org.fb.tools.LocalKnowledgeTools" -Dexec.args="getStructure" -q 2>nul
if errorlevel 1 (
    echo [WARNING] 直接运行测试失败，将使用集成测试方式
)

echo.
echo [测试2] 验证配置文件正确性
echo ----------------------------------------
echo 检查 KnowledgeBaseAssistantStream.java 中的tools配置：
findstr /C:"localKnowledgeTools" src\main\java\org\fb\service\assistant\KnowledgeBaseAssistantStream.java >nul
if not errorlevel 1 (
    echo   ✅ localKnowledgeTools 已配置
) else (
    echo   ❌ localKnowledgeTools 未配置
)

echo.
echo 检查 knowledge-base-prompt.txt 中的本地知识库指南：
findstr /C:"本地知识库" src\main\resources\knowledge-base-prompt.txt >nul
if not errorlevel 1 (
    echo   ✅ 本地知识库检索指南已添加
) else (
    echo   ❌ 本地知识库检索指南未添加
)

echo.
echo [测试3] 验证三一重工PDF文件存在
echo ----------------------------------------
if exist "knowledge\Financial Report Data\三一重工 2025 Q3.pdf" (
    echo   ✅ 找到三一重工 2025 Q3.pdf
    for %%I in ("knowledge\Financial Report Data\三一重工 2025 Q3.pdf") do (
        echo   文件大小: %%~zI bytes
    )
) else (
    echo   ❌ 未找到三一重工 2025 Q3.pdf
)

echo.
echo [测试4] 验证文本文件存在
echo ----------------------------------------
if exist "knowledge\Financial Report Data\三一重工_2025_Q3.txt" (
    echo   ✅ 找到三一重工_2025_Q3.txt
    for %%I in ("knowledge\Financial Report Data\三一重工_2025_Q3.txt") do (
        echo   文件大小: %%~zI bytes
    )
) else (
    echo   ❌ 未找到三一重工_2025_Q3.txt
)

echo.
echo [测试5] 验证索引文件
echo ----------------------------------------
if exist "knowledge\Financial Report Data\data_structure.md" (
    echo   ✅ 找到 data_structure.md
    findstr /C:"三一重工" knowledge\Financial Report Data\data_structure.md >nul
    if not errorlevel 1 (
        echo   ✅ 索引文件包含三一重工条目
    ) else (
        echo   ⚠️  索引文件未包含三一重工
    )
) else (
    echo   ❌ 未找到 data_structure.md
)

echo.
echo =========================================
echo   测试完成
echo =========================================
echo.
echo 修复总结：
echo 1. KnowledgeBaseAssistantStream.java - 已添加localKnowledgeTools
echo 2. knowledge-base-prompt.txt - 已添加本地知识库检索指南
echo 3. LocalKnowledgeTools.java - 已添加文件名模糊匹配
echo.
echo 下一步：
echo - 运行 deploy-desktop.bat 启动完整环境
echo - 访问 http://localhost:8000/chat-sse.html
echo - 测试询问："请帮我分析三一重工2025年第三季度报告"
echo.
pause
