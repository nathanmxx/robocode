#Requires -Version 5.1
<#
    Compila o robo e o instala em C:\robocode\robots para que o Robocode
    (GUI ou headless) o enxergue nativamente.

    Uso:  .\build.ps1
#>

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition

# ---------------------------------------------------------------- configuracao
$RobocodeHome = 'C:\robocode'
$SrcDir       = Join-Path $root 'src'
$OutDir       = Join-Path $root 'build\classes'
$DistDir      = Join-Path $root 'dist'
$Package      = 'nx'

# Bytecode alvo. Java 8 e proposital: o .class resultante carrega em qualquer
# JVM 8 ou superior, entao o robo funciona na maquina oficial do evento
# independentemente da versao de Java/Robocode instalada la.
$TargetRelease = '8'

# ------------------------------------------------------------- localizar javac
function Find-Javac {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\javac.exe') }
    $onPath = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }
    $candidates += Get-ChildItem 'C:\Program Files\Java','C:\Program Files\Eclipse Adoptium','C:\Program Files\JetBrains' `
                     -Filter 'javac.exe' -Recurse -Depth 4 -ErrorAction SilentlyContinue |
                   Select-Object -ExpandProperty FullName
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    throw 'javac nao encontrado. Instale um JDK ou defina JAVA_HOME.'
}

$javac = Find-Javac
Write-Host "javac : $javac" -ForegroundColor DarkGray

$robocodeJar = Join-Path $RobocodeHome 'libs\robocode.jar'
if (-not (Test-Path $robocodeJar)) { throw "robocode.jar nao encontrado em $robocodeJar" }

# ------------------------------------------------------------------- compilar
if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$sources = Get-ChildItem $SrcDir -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName
if (-not $sources) { throw "nenhum .java encontrado em $SrcDir" }

Write-Host "compilando $($sources.Count) arquivo(s) -> bytecode Java $TargetRelease" -ForegroundColor Cyan

$argv = @(
    '-nowarn'
    '-source', $TargetRelease
    '-target', $TargetRelease
    '-encoding', 'UTF-8'
    '-cp', $robocodeJar
    '-d', $OutDir
) + $sources

& $javac @argv
if ($LASTEXITCODE -ne 0) { throw "falha na compilacao (exit $LASTEXITCODE)" }

# .properties acompanham as classes
Get-ChildItem $SrcDir -Filter '*.properties' -Recurse | ForEach-Object {
    $rel = $_.FullName.Substring($SrcDir.Length).TrimStart('\')
    $dst = Join-Path $OutDir $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null
    Copy-Item $_.FullName $dst -Force
}

# ------------------------------------------------ instalar no diretorio de robos
$robotsDir = Join-Path $RobocodeHome "robots\$Package"
if (Test-Path $robotsDir) { Remove-Item $robotsDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $robotsDir | Out-Null
Copy-Item (Join-Path $OutDir "$Package\*") $robotsDir -Recurse -Force

# ------------------------------------------------- empacotar .jar para entrega
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
$jarExe = Join-Path (Split-Path $javac) 'jar.exe'
$jarOut = Join-Path $DistDir 'leviathan.jar'
if (Test-Path $jarExe) {
    if (Test-Path $jarOut) { Remove-Item $jarOut -Force }
    & $jarExe cf $jarOut -C $OutDir $Package
    if ($LASTEXITCODE -eq 0) { Write-Host "jar    : $jarOut" -ForegroundColor DarkGray }
}

$n = (Get-ChildItem $robotsDir -Filter '*.class').Count
Write-Host "OK - $n classes instaladas em $robotsDir" -ForegroundColor Green
