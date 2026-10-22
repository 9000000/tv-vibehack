# Script biên dịch CleanYourTV Manager APK (TV VibeHack Tool)
param(
    [switch]$Release = $true
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false

# Tìm JDK 17 hoặc 21
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jdkCandidate = @(
        "C:\Users\NongTuan\.jdks\jbr-21.0.11",
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
    ) | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1

    if ($jdkCandidate) {
        $env:JAVA_HOME = $jdkCandidate
        Write-Host "Sử dụng JDK: $env:JAVA_HOME" -ForegroundColor Cyan
    } else {
        throw "Không tìm thấy JDK 17+. Vui lòng thiết lập biến môi trường JAVA_HOME."
    }
}

$task = if ($Release) { ":manager:assembleRelease" } else { ":manager:assembleDebug" }
Write-Host "Đang biên dịch CleanYourTV Manager ($task)..." -ForegroundColor Green

Set-Location $PSScriptRoot
.\gradlew.bat -p . $task --console=plain

if ($LASTEXITCODE -eq 0) {
    $apkType = if ($Release) { "release\manager-release.apk" } else { "debug\manager-debug.apk" }
    $apkPath = Join-Path $PSScriptRoot "manager\build\outputs\apk\$apkType"
    Write-Host "`nBiên dịch thành công!" -ForegroundColor Green
    Write-Host "File APK tại: $apkPath" -ForegroundColor Yellow
} else {
    throw "Lỗi trong quá trình biên dịch Gradle."
}
