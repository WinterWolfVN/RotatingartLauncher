# 简化的游戏日志查看脚本
# 只显示关键的 ERROR、WARN 和重要信息

param(
    [switch]$Clear,
    [switch]$Follow,
    [int]$Lines = 50
)

if ($Clear) {
    adb logcat -c
    Write-Host "✅ 日志已清除" -ForegroundColor Green
}

Write-Host "📊 监控游戏日志（只显示关键信息）...`n" -ForegroundColor Yellow

$filter = @(
    "ERROR",
    "FATAL",
    "crash",
    "Signal",
    "Unknown OS",
    "Entry point",
    "Game execution",
    "Patch applied",
    "Bootstrap.*failed",
    "Exception"
)

$pattern = $filter -join "|"

if ($Follow) {
    # 持续监控模式
    adb logcat | Select-String -Pattern "Bootstrap|GameLauncher|tML" | Select-String -Pattern $pattern
} else {
    # 查看最近的日志
    adb logcat -d | Select-String -Pattern "Bootstrap|GameLauncher|tML" | Select-String -Pattern $pattern | Select-Object -Last $Lines
}

