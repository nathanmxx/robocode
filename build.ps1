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
$MainClass    = 'Leviathan'

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
# Todo pacote compilado vai para C:\robocode\robots: o de competicao (nx) e os
# bots de treino (spar), que precisam estar la para as batalhas de teste.
foreach ($pkgDir in Get-ChildItem $OutDir -Directory) {
    $dst = Join-Path $RobocodeHome "robots\$($pkgDir.Name)"
    if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    Copy-Item (Join-Path $pkgDir.FullName '*') $dst -Recurse -Force
}
$robotsDir = Join-Path $RobocodeHome "robots\$Package"

# O Robocode indexa os robos em robot.database e confia no cache. Sem invalidar,
# classes recem-compiladas so aparecem depois de um refresh manual na GUI.
$db = Join-Path $RobocodeHome 'robots\robot.database'
if (Test-Path $db) { Remove-Item $db -Force }

# ------------------------------------------------- empacotar .jar para entrega
# Um .jar e um zip; empacotamos via .NET para nao depender do jar.exe, que nao
# acompanha todos os runtimes (o JBR do PyCharm, por exemplo, nao tem).
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
$jarOut = Join-Path $DistDir 'leviathan.jar'
if (Test-Path $jarOut) { Remove-Item $jarOut -Force }

# Staging dentro do projeto de proposito: o TEMP do Windows vem como caminho
# curto (C:\Users\USURIO~2\...) e o '~' e interpretado pelo PowerShell como o
# diretorio home, quebrando Remove-Item.
$staging = Join-Path $root 'build\jar-staging'
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $staging $Package) | Out-Null
Copy-Item (Join-Path $OutDir "$Package\*") (Join-Path $staging $Package) -Recurse -Force

Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($staging, $jarOut)
Remove-Item $staging -Recurse -Force
Write-Host "jar    : $jarOut" -ForegroundColor DarkGray

# ------------------------------------------- versao de arquivo unico (compartilhar)
# Junta o robo inteiro em um .java so, para quem quiser copiar e colar no editor
# do proprio Robocode sem montar estrutura de pastas. Java aceita varias classes
# no mesmo arquivo desde que apenas uma seja public e tenha o nome do arquivo -
# entao as auxiliares perdem o 'public' e viram package-private.
$singleOut = Join-Path $DistDir "$MainClass.java"

$pkgSrc = Join-Path $SrcDir $Package
$ordered = Get-ChildItem $pkgSrc -Filter '*.java' |
           Sort-Object @{ Expression = { if ($_.BaseName -eq $MainClass) { 1 } else { 0 } } }, Name

$imports = $ordered | ForEach-Object { Get-Content $_.FullName } |
           Where-Object { $_ -match '^import ' } | Sort-Object -Unique

$out = New-Object System.Text.StringBuilder
[void]$out.AppendLine("package $Package;")
[void]$out.AppendLine()
foreach ($i in $imports) { [void]$out.AppendLine($i) }

foreach ($f in $ordered) {
    [void]$out.AppendLine()
    foreach ($line in Get-Content $f.FullName) {
        if ($line -match '^package ' -or $line -match '^import ') { continue }
        if ($f.BaseName -ne $MainClass) {
            $line = $line -replace '^public (final )?class ', '$1class '
        }
        [void]$out.AppendLine($line)
    }
}
# UTF-8 sem BOM: o Set-Content do PowerShell 5.1 escreve BOM e o javac recusa o
# arquivo com "illegal character: '﻿'".
[System.IO.File]::WriteAllText($singleOut, $out.ToString(), (New-Object System.Text.UTF8Encoding($false)))

# Gerar nao basta: so vale entregar o que compila.
$check = Join-Path $root 'build\single'
if (Test-Path $check) { Remove-Item $check -Recurse -Force }
New-Item -ItemType Directory -Force -Path $check | Out-Null
& $javac -nowarn -source $TargetRelease -target $TargetRelease -encoding UTF-8 `
         -cp $robocodeJar -d $check $singleOut
if ($LASTEXITCODE -ne 0) { throw 'a versao de arquivo unico nao compila' }
Write-Host "unico  : $singleOut" -ForegroundColor DarkGray

$n = (Get-ChildItem $robotsDir -Filter '*.class').Count
Write-Host "OK - $n classes instaladas em $robotsDir" -ForegroundColor Green
