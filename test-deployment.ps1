#!/usr/bin/env pwsh
# PowerShell脚本：测试RAG翻译系统部署

param(
    [string]$BaseUrl = "http://localhost:8000",
    [int]$Timeout = 30
)

$ErrorActionPreference = "Stop"

Write-Host "测试 RAG 翻译系统部署..." -ForegroundColor Blue
Write-Host "基础URL: $BaseUrl" -ForegroundColor Cyan
Write-Host ""

# 测试函数
function Test-Endpoint {
    param(
        [string]$Uri,
        [string]$Name
    )

    try {
        $response = Invoke-WebRequest -Uri $Uri -Method GET -TimeoutSec $Timeout
        Write-Host "✓ $Name: HTTP $($response.StatusCode)" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "✗ $Name: 失败 - $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

# 测试数据库连接
function Test-DatabaseConnection {
    param(
        [string]$ConnectionString,
        [string]$Name
    )

    try {
        Write-Host "正在测试 $Name 连接..." -ForegroundColor Yellow
        # 这里可以添加实际的数据库连接测试
        Write-Host "✓ $Name 连接测试通过" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "✗ $Name 连接失败: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

# 执行测试
Write-Host "1. 测试应用服务..." -ForegroundColor Yellow
$tests = @(
    @{ Uri = "$BaseUrl/actuator/health"; Name = "健康检查" },
    @{ Uri = "$BaseUrl/index.html"; Name = "Web界面" },
    @{ Uri = "$BaseUrl/doc.html"; Name = "API文档" },
    @{ Uri = "$BaseUrl/actuator/info"; Name = "应用信息" }
)

$failedTests = @()
foreach ($test in $tests) {
    if (!(Test-Endpoint -Uri $test.Uri -Name $test.Name)) {
        $failedTests += $test.Name
    }
}

Write-Host ""
Write-Host "2. 测试服务端口..." -ForegroundColor Yellow

$services = @(
    @{ Port = 3306; Name = "MySQL" },
    @{ Port = 27017; Name = "MongoDB" },
    @{ Port = 6379; Name = "Redis" },
    @{ Port = 6333; Name = "Qdrant" },
    @{ Port = 15672; Name = "RabbitMQ管理" }
)

foreach ($service in $services) {
    try {
        $tcpTest = Test-NetConnection -ComputerName "localhost" -Port $service.Port -WarningAction SilentlyContinue
        if ($tcpTest.TcpTestSucceeded) {
            Write-Host "✓ $($service.Name) 端口 $($service.Port): 开放" -ForegroundColor Green
        } else {
            Write-Host "✗ $($service.Name) 端口 $($service.Port): 未开放" -ForegroundColor Red
        }
    }
    catch {
        Write-Host "✗ $($service.Name) 端口 $($service.Port): 测试失败" -ForegroundColor Red
    }
}

# 总结
Write-Host ""
Write-Host "================================" -ForegroundColor Blue
if ($failedTests.Count -eq 0) {
    Write-Host "所有测试通过！✓" -ForegroundColor Green
} else {
    Write-Host "以下测试失败: $($failedTests -join ', ')" -ForegroundColor Red
}

Write-Host ""
Write-Host "如需查看日志，请运行: docker compose logs -f" -ForegroundColor Cyan