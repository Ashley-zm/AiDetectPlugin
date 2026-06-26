<#
.SYNOPSIS
    AiDetectPlugin 构建 / 发布打包脚本（遵循本项目既有构建习惯）。

.DESCRIPTION
    按本仓库的固定发布流程，一步完成：
      1. 读取 nativeplugins/AiDetectPlugin/package.json 里的 version 作为发布版本号；
      2. 用 Gradle Wrapper 构建 release AAR（:AiDetectPlugin:assembleRelease，含 arm64-v8a / armeabi-v7a 原生库）；
      3. 把产物同步到 nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar；
      4. 生成发布备份 releases/AiDetectPlugin-v{version}/（android/AiDetectPlugin-release.aar + package.json）；
      5. 打成 releases/AiDetectPlugin-v{version}.zip（与历史归档一致：内容置于 zip 根目录，不再包一层版本目录）。

    脚本不会自动修改 package.json 的 version，也不会改写 CHANGELOG.md —— 这两件事是人工决定。
    发新版本前请先手动把 package.json 的 version 改好，再运行本脚本；打包完成后按既有格式补写 releases/CHANGELOG.md。

.PARAMETER Version
    覆盖发布版本号（默认读取 package.json 的 version）。一般无需传，改 package.json 即可。

.PARAMETER NoArchive
    只构建并同步 AAR，跳过 releases 归档与 zip 打包（日常本地重建用）。

.PARAMETER Clean
    构建前先执行 :AiDetectPlugin:clean，做一次干净构建。

.PARAMETER Force
    允许覆盖已存在的同版本归档（releases/AiDetectPlugin-v{version}/ 与 .zip）。
    默认拒绝覆盖，以保护历史发布归档；只有在确实要重打同一版本时才加 -Force。

.EXAMPLE
    .\build-release.ps1
    读取 package.json 版本，构建 + 同步 + 归档 + 打 zip。

.EXAMPLE
    .\build-release.ps1 -NoArchive
    只重建并同步 AAR，不打发布包。

.EXAMPLE
    .\build-release.ps1 -Clean -Force
    干净构建，并允许覆盖当前版本的归档。
#>
[CmdletBinding()]
param(
    [string]$Version,
    [switch]$NoArchive,
    [switch]$Clean,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

# 以脚本所在目录为仓库根（settings.gradle / gradlew.bat 所在处）
$RepoRoot    = $PSScriptRoot
$PluginDir   = Join-Path $RepoRoot 'nativeplugins\AiDetectPlugin'
$PackageJson = Join-Path $PluginDir 'package.json'
$BuiltAar    = Join-Path $PluginDir 'android-src\build\outputs\aar\AiDetectPlugin-release.aar'
$SyncedAar   = Join-Path $PluginDir 'android\AiDetectPlugin-release.aar'
$ReleasesDir = Join-Path $RepoRoot 'releases'
$GradleWcmd  = Join-Path $RepoRoot 'gradlew.bat'

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

# ---- 0. 前置校验 ------------------------------------------------------------
if (-not (Test-Path $PackageJson)) { throw "找不到 package.json：$PackageJson" }
if (-not (Test-Path $GradleWcmd))  { throw "找不到 gradlew.bat：$GradleWcmd" }

# ---- 1. 解析发布版本号 ------------------------------------------------------
if ([string]::IsNullOrWhiteSpace($Version)) {
    $pkg = Get-Content $PackageJson -Raw | ConvertFrom-Json
    $Version = $pkg.version
}
if ([string]::IsNullOrWhiteSpace($Version)) { throw "无法确定版本号：package.json 缺少 version 字段，且未传 -Version。" }
Write-Host "AiDetectPlugin 构建发布" -ForegroundColor Green
Write-Host "  版本号 : $Version"
Write-Host "  归档   : $(if ($NoArchive) { '跳过（-NoArchive）' } else { "releases/AiDetectPlugin-v$Version[.zip]" })"

# ---- 2. Gradle 构建 release AAR --------------------------------------------
$gradleArgs = @()
if ($Clean) { $gradleArgs += ':AiDetectPlugin:clean' }
$gradleArgs += ':AiDetectPlugin:assembleRelease'
$gradleArgs += '--console=plain'

Write-Step "Gradle 构建：$($gradleArgs -join ' ')"
Push-Location $RepoRoot
try {
    & $GradleWcmd @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败（退出码 $LASTEXITCODE）。" }
} finally {
    Pop-Location
}

if (-not (Test-Path $BuiltAar)) { throw "构建完成但未找到产物 AAR：$BuiltAar" }

# ---- 3. 同步 AAR 到 nativeplugins/.../android/ ------------------------------
Write-Step "同步 AAR 到插件目录"
$syncedDir = Split-Path $SyncedAar -Parent
if (-not (Test-Path $syncedDir)) { New-Item -ItemType Directory -Path $syncedDir -Force | Out-Null }
Copy-Item -Path $BuiltAar -Destination $SyncedAar -Force
$aarSizeMB = [math]::Round((Get-Item $SyncedAar).Length / 1MB, 2)
Write-Host "  已同步 -> $SyncedAar （$aarSizeMB MB）"

if ($NoArchive) {
    Write-Host ""
    Write-Host "完成（仅构建 + 同步，未打发布包）。" -ForegroundColor Green
    return
}

# ---- 4. 生成发布备份 releases/AiDetectPlugin-v{version}/ --------------------
$archiveName = "AiDetectPlugin-v$Version"
$archiveDir  = Join-Path $ReleasesDir $archiveName
$zipPath     = Join-Path $ReleasesDir "$archiveName.zip"

if ((Test-Path $archiveDir) -or (Test-Path $zipPath)) {
    if (-not $Force) {
        throw "发布归档已存在：$archiveName（目录或 zip）。为保护历史发布，默认不覆盖。请改 package.json 的 version 发新版本，或在确需重打同一版本时加 -Force。"
    }
    Write-Host "  -Force：移除已存在的同版本归档..." -ForegroundColor Yellow
    if (Test-Path $archiveDir) { Remove-Item $archiveDir -Recurse -Force }
    if (Test-Path $zipPath)    { Remove-Item $zipPath -Force }
}

Write-Step "生成发布备份 $archiveName/"
New-Item -ItemType Directory -Path (Join-Path $archiveDir 'android') -Force | Out-Null
Copy-Item -Path $SyncedAar   -Destination (Join-Path $archiveDir 'android\AiDetectPlugin-release.aar') -Force
Copy-Item -Path $PackageJson -Destination (Join-Path $archiveDir 'package.json') -Force

# ---- 5. 打 zip（内容置于 zip 根目录，与历史归档保持一致） -------------------
Write-Step "打包 $archiveName.zip"
Compress-Archive -Path (Join-Path $archiveDir '*') -DestinationPath $zipPath -Force
$zipSizeMB = [math]::Round((Get-Item $zipPath).Length / 1MB, 2)
Write-Host "  已生成 -> $zipPath （$zipSizeMB MB）"

# ---- 完成 -------------------------------------------------------------------
Write-Host ""
Write-Host "发布打包完成：$archiveName" -ForegroundColor Green
Write-Host "  - $SyncedAar"
Write-Host "  - $archiveDir\"
Write-Host "  - $zipPath"
Write-Host ""
Write-Host "下一步（人工）：按既有格式在 releases/CHANGELOG.md 顶部补写 v$Version 的变更记录。" -ForegroundColor Yellow
