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
$Package      = 'tcn'
$MainClass    = 'TCN'

# Nome do robo entregue ao jurado. O item 8 do regulamento manda criar um
# diretorio com o nome da equipe e por o .java de mesmo nome dentro dele, como em
# C:\Robocode\Robots\Equipe1\Equipe1.java - entao pasta, arquivo, pacote e classe
# precisam ser todos o mesmo nome.
#
# A equipe se chama TCN-bots, e o hifen NAO e valido em identificador Java:
# "package TCN-bots;" nao compila. TCNbots e o nome sem o caractere proibido.
# Pendente de confirmacao com a comissao.
$DeliveryName = 'TCNbots'

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
# Todo pacote compilado vai para C:\robocode\robots: o de competicao (tcn) e os
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
$jarOut = Join-Path $DistDir 'tcn.jar'
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
$singleOut = Join-Path $DistDir "$DeliveryName.java"

$pkgSrc = Join-Path $SrcDir $Package
$ordered = Get-ChildItem $pkgSrc -Filter '*.java' |
           Sort-Object @{ Expression = { if ($_.BaseName -eq $MainClass) { 1 } else { 0 } } }, Name

$imports = $ordered | ForEach-Object { Get-Content $_.FullName } |
           Where-Object { $_ -match '^import ' } | Sort-Object -Unique

# O pacote do entregavel e TCN, com T maiusculo, e nao o tcn de src/.
#
# Exigencia do item 8 do regulamento: "deve ser criado um diretorio com o mesmo
# nome do programa que deve ser o nome da equipe", com o exemplo
# C:\Robocode\Robots\Equipe1\Equipe1.java. Ou seja, o robo fica DENTRO de uma
# pasta com o nome da equipe, e em Java o pacote tem que casar com essa pasta.
#
# O nome vai igual ao da pasta, caixa inclusive: o Windows nao diferencia
# maiuscula em nome de pasta, mas o Robocode monta o nome do robo a partir do
# diretorio, e divergir ai e pedir para ele nao achar o robo.
$out = New-Object System.Text.StringBuilder
[void]$out.AppendLine("package $DeliveryName;")
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
# ---------------------------------------------- enxugar o arquivo de entrega
# O codigo em src/ fica comentado, porque e ele que se le e se mantem. Ja o
# arquivo entregue no dia sai sem comentario e sem linha em branco: quem recebe
# vai colar e compilar, nao ler, e um arquivo curto nao assusta.
#
# Seguro remover por texto porque nenhuma string do codigo contem // nem /*,
# verificado antes de escrever isto.
$linhas = $out.ToString() -split "`r?`n"
$enxuto = New-Object System.Text.StringBuilder
$dentroDeBloco = $false

foreach ($linha in $linhas) {
    $t = $linha

    if ($dentroDeBloco) {
        $fim = $t.IndexOf('*/')
        if ($fim -lt 0) { continue }
        $t = $t.Substring($fim + 2)
        $dentroDeBloco = $false
    }

    while ($true) {
        $ini = $t.IndexOf('/*')
        if ($ini -lt 0) { break }
        $fim = $t.IndexOf('*/', $ini + 2)
        if ($fim -lt 0) { $t = $t.Substring(0, $ini); $dentroDeBloco = $true; break }
        $t = $t.Substring(0, $ini) + $t.Substring($fim + 2)
    }

    $barra = $t.IndexOf('//')
    if ($barra -ge 0) { $t = $t.Substring(0, $barra) }

    if ($t.Trim().Length -eq 0) { continue }

    # A classe principal passa a se chamar como a equipe, porque o arquivo tem
    # que se chamar como a pasta e em Java a classe publica tem que se chamar
    # como o arquivo. Trocado por texto, o que so e seguro porque nenhuma string
    # do codigo contem TCN - verificado antes de escrever isto.
    if ($MainClass -ne $DeliveryName) {
        $t = $t -replace "\b$MainClass\b", $DeliveryName
    }

    [void]$enxuto.AppendLine($t.TrimEnd())
}

# UTF-8 sem BOM: o Set-Content do PowerShell 5.1 escreve BOM e o javac recusa o
# arquivo com "illegal character: '﻿'".
[System.IO.File]::WriteAllText($singleOut, $enxuto.ToString(), (New-Object System.Text.UTF8Encoding($false)))

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
