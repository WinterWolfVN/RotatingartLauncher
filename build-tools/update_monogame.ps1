param(
    [string]$GameDirAndroid = "/storage/emulated/0/Android/data/com.app.ralaunch/files/games",
    [string]$TargetGameName = "Stardew Valley"
)

$ErrorActionPreference = 'Stop'

function Build-MonoGameFrameworkDll {
    Push-Location "$PSScriptRoot/mg-custom"
    try {
        dotnet build -c Release | Out-Null
        $out = Join-Path "$PSScriptRoot/mg-custom/bin/Release/net8.0" "MonoGame.Framework.dll"
        if (-not (Test-Path $out)) { throw "MonoGame.Framework.dll not found after build: $out" }
        return $out
    } finally {
        Pop-Location
    }
}

function Find-GameFolderOnDevice {
    $list = (adb shell ls -1 "$GameDirAndroid" 2>$null) -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
    $cand = $list | Where-Object { $_ -match "$TargetGameName" } | Select-Object -First 1
    if (-not $cand) { throw "Game folder not found under $GameDirAndroid containing '$TargetGameName'" }
    return "$GameDirAndroid/$cand/GoG Games/$TargetGameName"
}

Write-Host "[1/4] Building MonoGame.Framework.dll ..."
$dll = Build-MonoGameFrameworkDll
Write-Host "Built: $dll"

Write-Host "[2/4] Locating game folder on device ..."
$remoteGamePath = Find-GameFolderOnDevice
Write-Host "Remote: $remoteGamePath"

$remoteDll = "$remoteGamePath/MonoGame.Framework.dll"
$remoteBak = "$remoteGamePath/MonoGame.Framework.dll.bak"

Write-Host "[3/4] Backing up original DLL and pushing update ..."
adb shell "if [ -f '$remoteDll' ]; then cp '$remoteDll' '$remoteBak'; fi" | Out-Null
adb push "$dll" "$remoteDll" | Out-Null

Write-Host "[4/4] Done. Verifying file ..."
adb shell ls -l "$remoteDll"

Write-Host "You can now start the game and check /storage/emulated/0/Android/data/com.app.ralaunch/files/touch_debug.txt"


