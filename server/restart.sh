#!/bin/bash

set -e

# 环境参数解析
ENV="${1:-dev}"
echo "===================================="
echo "OpenClaw WeCom 服务重启脚本"
echo "当前环境: $ENV"
echo "===================================="
echo "开始时间: $(date)"
echo ""

# 切换到项目目录
cd "$(dirname "$0")"
echo "当前目录: $(pwd)"
echo ""

# 设置 Spring Profile
export SPRING_PROFILES_ACTIVE="$ENV"
echo "激活 Profile: $SPRING_PROFILES_ACTIVE"
echo ""

# 1. 更新代码
echo "1. 拉取最新代码..."
git pull
if [ $? -ne 0 ]; then
    echo "错误: 代码拉取失败"
    exit 1
fi
echo "✓ 代码更新完成"
echo ""

# 2. 构建项目
echo "2. 构建项目..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "错误: 项目构建失败"
    exit 1
fi
echo "✓ 项目构建完成"
echo ""

# 3. 停止旧服务
echo "3. 停止旧服务..."
PID=$(ps aux | grep "openclaw-relay" | grep -v grep | awk '{print $2}')
if [ -n "$PID" ]; then
    echo "正在停止进程: $PID"
    kill $PID
    sleep 3
    # 检查是否还有进程
    PID=$(ps aux | grep "openclaw-relay" | grep -v grep | awk '{print $2}')
    if [ -n "$PID" ]; then
        echo "强制终止进程: $PID"
        kill -9 $PID
        sleep 2
    fi
fi
echo "✓ 旧服务已停止"
echo ""

# 4. 启动新服务
echo "4. 启动新服务 (环境: $ENV)..."
java -Duser.timezone=Asia/Shanghai -jar -Dspring.profiles.active="$ENV" target/openclaw-relay-1.0.0.jar > app.log 2>&1 &
PID=$!
echo "服务已启动，进程ID: $PID"
echo ""

# 5. 检查服务状态
echo "5. 检查服务状态..."
sleep 8
HEALTH_STATUS=$(curl -s http://localhost:8080/relay/check/health)
if [ $? -eq 0 ]; then
    echo "✓ 服务健康检查成功"
    echo "服务状态: $HEALTH_STATUS"
else
    echo "⚠️  服务健康检查失败，查看日志了解详情"
    tail -20 app.log
    exit 1
fi
echo ""

echo "===================================="
echo "重启完成！"
echo "结束时间: $(date)"
echo "===================================="
