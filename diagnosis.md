# 服务连接拒绝问题诊断指南

请在Ubuntu虚拟机（192.168.179.130）上执行以下步骤：

## 1. 检查Docker容器状态
```bash
docker ps
```

## 2. 查看容器日志
```bash
docker logs rag-translation
```

## 3. 检查端口映射
```bash
docker port rag-translation
```

## 4. 检查防火墙设置
```bash
sudo ufw status
# 如需要，开放8000端口
sudo ufw allow 8000
```

## 5. 检查服务内部状态
```bash
docker exec -it rag-translation curl http://localhost:8000/actuator/health
```

## 6. 检查网络配置
```bash
docker network inspect app-network
```

## 7. 重启服务
```bash
docker-compose down
docker-compose up -d
```

## 8. 检查主机网络配置
```bash
ip addr show
```

# 常见问题及解决方案

### 问题1：容器未运行
**解决方法**：查看容器日志，修复错误后重启容器

### 问题2：端口未映射
**解决方法**：检查docker-compose.yml中的ports配置

### 问题3：服务未启动
**解决方法**：查看容器日志，检查应用启动错误

### 问题4：防火墙阻止
**解决方法**：开放8000端口

### 问题5：网络配置错误
**解决方法**：检查docker网络配置

# 手动测试步骤

1. 先在虚拟机内部测试：
   ```bash
   curl http://localhost:8000
   ```

2. 再从外部测试：
   ```bash
   curl http://192.168.179.130:8000
   ```

请执行以上步骤，并将结果反馈给我，以便进一步分析问题。