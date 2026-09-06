$ErrorActionPreference = 'Stop'

$MavenDir = "maven-local"
$MvnPath = ".\$MavenDir\bin\mvn.cmd"

if (!(Test-Path $MvnPath)) {
    Write-Host "Maven não encontrado. Baixando Maven localmente..."
    $MavenVersion = "3.9.9"
    $MavenUrl = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
    Invoke-WebRequest -Uri $MavenUrl -OutFile "maven.zip"
    Write-Host "Extraindo Maven..."
    Expand-Archive -Path "maven.zip" -DestinationPath "." -Force
    if (Test-Path $MavenDir) { Remove-Item -Path $MavenDir -Recurse -Force }
    Rename-Item -Path "apache-maven-$MavenVersion" -NewName $MavenDir
    Remove-Item "maven.zip"
    Write-Host "Maven baixado com sucesso!"
}

Write-Host "Construindo o plugin..."
& $MvnPath package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro na compilação do plugin!"
    exit $LASTEXITCODE
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

Write-Host "Iniciando o servidor..."
Set-Location $ServerDir
java -Xms2G -Xmx2G -jar server.jar --nogui
