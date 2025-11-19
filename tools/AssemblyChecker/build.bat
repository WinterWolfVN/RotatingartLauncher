@echo off
echo ========================================
echo Building AssemblyChecker
echo ========================================

cd /d %~dp0

REM 清理旧的构建
if exist bin rd /s /q bin
if exist obj rd /s /q obj

REM 编译项目
dotnet publish -c Release -r android-arm64 --self-contained false

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build successful!
    echo ========================================
    echo.
    echo Output: bin\Release\net8.0\android-arm64\publish\
    echo.

    REM 复制到 assets 目录
    echo Copying to assets...
    xcopy /Y /I bin\Release\net8.0\android-arm64\publish\*.dll ..\..\app\src\main\assets\tools\AssemblyChecker\
    xcopy /Y /I bin\Release\net8.0\android-arm64\publish\*.json ..\..\app\src\main\assets\tools\AssemblyChecker\

    echo.
    echo Done!
) else (
    echo.
    echo ========================================
    echo Build failed!
    echo ========================================
)

pause
