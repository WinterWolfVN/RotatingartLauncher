# Pack Bootstrap for .NET 7.0
$sourceDir = ".\bin\Release\net7.0"
$zipPath = "..\..\app\src\main\assets\Bootstrap.zip"

# Remove old zip if exists
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
    Write-Host "Removed old Bootstrap.zip"
}

# Create zip
Compress-Archive -Path "$sourceDir\*" -DestinationPath $zipPath -CompressionLevel Optimal
Write-Host "✓ Bootstrap.zip created successfully for .NET 7.0"
Write-Host "  Location: $zipPath"

# Show file info
$zipInfo = Get-Item $zipPath
Write-Host "  Size: $([math]::Round($zipInfo.Length / 1MB, 2)) MB"

