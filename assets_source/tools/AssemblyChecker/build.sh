#!/bin/bash
# 构建 AssemblyChecker 工具

echo "构建 AssemblyChecker..."

# 发布为自包含应用（适用于 Android）
dotnet publish -c Release -r android-arm64 --self-contained false -o ./bin/publish

if [ $? -eq 0 ]; then
    echo "✓ 构建成功"
    echo "输出目录: ./bin/publish"
    ls -lh ./bin/publish
else
    echo "✗ 构建失败"
    exit 1
fi
