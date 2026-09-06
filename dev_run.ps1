param(
    [switch]$BuildOnly
)
$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
Push-Location $projectRoot
$packServer = $null

try {

$MavenDir = ".tools\maven"
$MvnPath = ".\$MavenDir\bin\mvn.cmd"
$MavenLauncher = Get-ChildItem "$MavenDir\boot\plexus-classworlds-*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*.license" } |
    Select-Object -First 1

if (!(Test-Path $MvnPath) -or $null -eq $MavenLauncher) {
    Write-Host "Maven não encontrado. Baixando Maven localmente..."
    $MavenVersion = "3.9.9"
    $MavenUrl = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
    $MavenArchive = Join-Path $env:TEMP "aethermc-maven-$MavenVersion.zip"
    Invoke-WebRequest -Uri $MavenUrl -OutFile $MavenArchive
    Write-Host "Extraindo Maven..."
    New-Item -ItemType Directory -Path ".tools" -Force | Out-Null
    if (Test-Path $MavenDir) { Remove-Item -Path $MavenDir -Recurse -Force }
    $ExtractedMaven = ".tools\apache-maven-$MavenVersion"
    if (Test-Path $ExtractedMaven) { Remove-Item -Path $ExtractedMaven -Recurse -Force }
    Expand-Archive -Path $MavenArchive -DestinationPath ".tools" -Force
    Move-Item -Path $ExtractedMaven -Destination $MavenDir
    Remove-Item $MavenArchive
    Write-Host "Maven baixado com sucesso!"
}

Write-Host "Construindo o plugin..."
& $MvnPath --batch-mode --no-transfer-progress package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro na compilação do plugin!"
    exit $LASTEXITCODE
}

if ($BuildOnly) {
    $BuildOnlyPluginsDir = "test-server\plugins"
    New-Item -ItemType Directory -Force -Path $BuildOnlyPluginsDir | Out-Null
    Copy-Item "target\SkyblockGenerators.jar" -Destination "$BuildOnlyPluginsDir\SkyblockGenerators.jar" -Force
    Write-Host "Plugin copiado para $BuildOnlyPluginsDir\SkyblockGenerators.jar"
    Write-Host "Build concluído."
    exit 0
}

$ServerDir = "test-server"
if (!(Test-Path $ServerDir)) {
    New-Item -ItemType Directory -Force -Path $ServerDir | Out-Null
}

$Version = "1.21.4"
$ServerJar = "$ServerDir\server.jar"

if (!(Test-Path $ServerJar)) {
    Write-Host "Baixando Servidor $Version (Purpur, compatível com Paper)..."
    $DownloadUrl = "https://api.purpurmc.org/v2/purpur/$Version/latest/download"
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $ServerJar
    Write-Host "Download concluído."
}

Write-Host "Aceitando EULA..."
Set-Content -Path "$ServerDir\eula.txt" -Value "eula=true"
if (!(Test-Path "$ServerDir\server.properties")) {
    Set-Content -Path "$ServerDir\server.properties" -Value "enforce-secure-profile=false"
} else {
    $ServerProperties = Get-Content "$ServerDir\server.properties" -Raw
    if ($ServerProperties -match '(?m)^enforce-secure-profile=') {
        $ServerProperties = $ServerProperties -replace '(?m)^enforce-secure-profile=.*$', 'enforce-secure-profile=false'
    } else {
        $ServerProperties += "`nenforce-secure-profile=false`n"
    }
    Set-Content -Path "$ServerDir\server.properties" -Value $ServerProperties -NoNewline
}

$PluginsDir = "$ServerDir\plugins"
if (!(Test-Path $PluginsDir)) {
    New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null
}

$ViaVersionJar = "$PluginsDir\ViaVersion.jar"
if (!(Test-Path $ViaVersionJar)) {
    Write-Host "Baixando ViaVersion (para suportar clientes 1.20.2+)..."
    $ViaVersions = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/viaversion/version"
    $ViaUrl = $ViaVersions[0].files[0].url
    Invoke-WebRequest -Uri $ViaUrl -OutFile $ViaVersionJar
    Write-Host "Download do ViaVersion concluído."
}

Write-Host "Copiando plugin atualizado..."
Copy-Item "target\SkyblockGenerators.jar" -Destination "$PluginsDir\SkyblockGenerators.jar" -Force

$RuntimeConfig = "$PluginsDir\SkyblockGenerators\config.yml"
$PackFile = "$projectRoot\resource-pack\AetherMC-resource-pack.zip"
if ((Test-Path $RuntimeConfig) -and (Test-Path $PackFile)) {
    $PackSha1 = (Get-FileHash $PackFile -Algorithm SHA1).Hash.ToLowerInvariant()
    $RuntimeConfigContent = Get-Content $RuntimeConfig -Raw
    $RuntimeConfigContent = $RuntimeConfigContent -replace 'sha1:\s*"[0-9a-fA-F]{40}"', "sha1: `"$PackSha1`""
    Set-Content -Path $RuntimeConfig -Value $RuntimeConfigContent -NoNewline
}

Write-Host "Iniciando servidor local do resource pack..."
$packServer = Start-Process -FilePath "python" `
    -ArgumentList "-m http.server 8765 --bind 127.0.0.1 --directory resource-pack" `
    -WorkingDirectory $projectRoot -PassThru -WindowStyle Hidden

Write-Host "Iniciando o servidor..."
Set-Location $ServerDir
java -Xms2G -Xmx2G -jar server.jar --nogui
}
finally {
    if ($null -ne $packServer -and !$packServer.HasExited) {
        Stop-Process -Id $packServer.Id
    }
    Pop-Location
}
