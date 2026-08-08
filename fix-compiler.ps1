#Requires -Version 5.1
<#
    Faz o editor do Robocode voltar a compilar.

    O erro "Java Compiler (javac) does not exists or cannot compile" acontece
    quando a maquina tem so um JRE (que nao traz javac) e o java do PATH e
    antigo demais para o compilador reserva embutido, o Eclipse ECJ, que exige
    Java 17+.

    Este script procura um JDK de verdade, contorna o problema de caminho com
    espaco e escreve a configuracao do compilador do Robocode.

    Uso:  .\fix-compiler.ps1
#>

$ErrorActionPreference = 'Stop'
$RobocodeHome = 'C:\robocode'

if (-not (Test-Path $RobocodeHome)) { throw "Robocode nao encontrado em $RobocodeHome" }

# ------------------------------------------------------------ achar um javac
$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\javac.exe') }
$candidates += Get-ChildItem 'C:\Program Files\Java','C:\Program Files\Eclipse Adoptium',
                             'C:\Program Files\JetBrains','C:\Program Files (x86)\Java' `
                 -Filter 'javac.exe' -Recurse -Depth 5 -ErrorAction SilentlyContinue |
               Select-Object -ExpandProperty FullName

$best = $null; $bestVersion = 0
foreach ($c in $candidates) {
    if (-not ($c -and (Test-Path $c))) { continue }
    $raw = & $c -version 2>&1 | Select-Object -First 1
    if ($raw -match '(\d+)\.(\d+)') {
        $v = if ($matches[1] -eq '1') { [int]$matches[2] } else { [int]$matches[1] }
        if ($v -gt $bestVersion) { $bestVersion = $v; $best = $c }
    }
}

if (-not $best) {
    Write-Host 'Nenhum JDK encontrado - so ha JRE nesta maquina.' -ForegroundColor Red
    Write-Host 'Instale um JDK (https://adoptium.net) ou use o .jar ja compilado.'
    exit 1
}
Write-Host "javac  : $best (Java $bestVersion)" -ForegroundColor DarkGray

# ------------------------------------------- caminho sem espacos, se precisar
# O Robocode monta o comando de compilacao concatenando texto e depois faz
# split, entao um caminho com espaco vira dois argumentos e nada funciona.
# A juncao e um link de diretorio: nao copia nada e se desfaz com rmdir.
$javac = $best
if ($javac -match ' ') {
    $jdkHome = Split-Path (Split-Path $javac)      # ...\bin\javac.exe -> ...\
    $link = Join-Path $RobocodeHome 'jdk'
    if (Test-Path $link) { cmd /c rmdir "$link" | Out-Null }
    cmd /c mklink /J "$link" "$jdkHome" | Out-Null
    $javac = "$RobocodeHome/jdk/bin/javac.exe"
    Write-Host "link   : $link -> $jdkHome" -ForegroundColor DarkGray
}
$javac = $javac -replace '\\', '/'

# ------------------------------------------------------ escrever a config
# --release 8 e proposital: o robo compilado carrega em qualquer JVM 8 ou
# superior, entao funciona seja qual for o Java que abrir o Robocode.
$configDir = Join-Path $RobocodeHome 'config'
New-Item -ItemType Directory -Force -Path $configDir | Out-Null

$content = @"
#Robot Compiler Properties
#Gerado por fix-compiler.ps1
compiler.binary=$javac
compiler.classpath=-classpath $RobocodeHome/libs/robocode.jar
compiler.options=-encoding UTF-8 --release 8 -Xlint:-options
"@ -replace '\\', '/'

$target = Join-Path $configDir 'compiler.properties'
[System.IO.File]::WriteAllText($target, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "config : $target" -ForegroundColor DarkGray

# ------------------------------------------------------------------ conferir
$test = Join-Path $RobocodeHome 'compilers\CompilerTest.java'
if (Test-Path $test) {
    Push-Location $RobocodeHome
    try {
        & $javac -encoding UTF-8 --release 8 -Xlint:-options `
                 -classpath "$RobocodeHome/libs/robocode.jar" $test 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host 'OK - o editor do Robocode ja compila.' -ForegroundColor Green
        } else {
            Write-Host 'O compilador foi configurado, mas o teste falhou.' -ForegroundColor Yellow
        }
    } finally { Pop-Location }
}
