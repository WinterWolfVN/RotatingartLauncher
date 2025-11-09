# Pack Bootstrap for .NET 8.0 (with all dependencies)
# 这个脚本会打包 Bootstrap.dll 和所有依赖 DLL 到 assets/Bootstrap.zip

$targetFramework = "net8.0"
$sourceDir = ".\bin\Release\$targetFramework"
$zipPath = "..\..\app\src\main\assets\Bootstrap.zip"

Write-Host "================================================"
Write-Host "  Bootstrap Packer for .NET $targetFramework"
Write-Host "================================================"
Write-Host ""

# Check if source directory exists
if (-not (Test-Path $sourceDir)) {
    Write-Host "❌ Error: Source directory not found: $sourceDir" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please build the project first:" -ForegroundColor Yellow
    Write-Host "  dotnet build -c Release -f $targetFramework" -ForegroundColor Cyan
    exit 1
}

# List required files
$requiredFiles = @(
    "Bootstrap.dll",
    "0Harmony.dll",
    "Mono.Cecil.dll",
    "Mono.Cecil.Mdb.dll",
    "Mono.Cecil.Pdb.dll",
    "Mono.Cecil.Rocks.dll",
    "MonoMod.Backports.dll",
    "MonoMod.Core.dll",
    "MonoMod.Iced.dll",
    "MonoMod.RuntimeDetour.dll",
    "MonoMod.Utils.dll",
    "Ico.Reader.dll",
    "PeDecoder.dll"
)

Write-Host "Checking required files..." -ForegroundColor Cyan
$missingFiles = @()
foreach ($file in $requiredFiles) {
    $filePath = Join-Path $sourceDir $file
    if (Test-Path $filePath) {
        $fileSize = (Get-Item $filePath).Length
        Write-Host "  ✓ $file" -NoNewline -ForegroundColor Green
        Write-Host " ($([math]::Round($fileSize / 1KB, 1)) KB)" -ForegroundColor Gray
    } else {
        Write-Host "  ✗ $file (missing)" -ForegroundColor Red
        $missingFiles += $file
    }
}

if ($missingFiles.Count -gt 0) {
    Write-Host ""
    Write-Host "❌ Error: $($missingFiles.Count) required file(s) missing!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Creating Bootstrap.zip..." -ForegroundColor Cyan

# Remove old zip if exists
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
    Write-Host "  Removed old Bootstrap.zip" -ForegroundColor Gray
}

# Only include .dll files (exclude .exe, .pdb, .json, etc.)
$dllFiles = Get-ChildItem -Path $sourceDir -Filter "*.dll"
$tempZipDir = Join-Path $env:TEMP "bootstrap_pack_$(Get-Date -Format 'yyyyMMddHHmmss')"
New-Item -ItemType Directory -Path $tempZipDir -Force | Out-Null

# Copy only DLL files to temp directory
foreach ($dll in $dllFiles) {
    Copy-Item $dll.FullName -Destination $tempZipDir
}

# Create zip from temp directory
Compress-Archive -Path "$tempZipDir\*" -DestinationPath $zipPath -CompressionLevel Optimal

# Clean up temp directory
Remove-Item $tempZipDir -Recurse -Force

Write-Host ""
Write-Host "✓ Bootstrap.zip created successfully!" -ForegroundColor Green
Write-Host "  Location: $zipPath" -ForegroundColor Gray
Write-Host "  Target Framework: $targetFramework" -ForegroundColor Gray

# Show file info
$zipInfo = Get-Item $zipPath
Write-Host "  Size: $([math]::Round($zipInfo.Length / 1MB, 2)) MB" -ForegroundColor Gray
Write-Host "  Files included: $($dllFiles.Count) DLL(s)" -ForegroundColor Gray

Write-Host ""
Write-Host "================================================"
Write-Host "  Ready to use in Android app!"
Write-Host "================================================"
