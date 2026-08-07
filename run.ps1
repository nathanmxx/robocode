#Requires -Version 5.1
<#
    Abre o Robocode com uma JVM moderna.

    Por que existe: o robocode.bat usa o 'java' do PATH, que nesta maquina e o
    Java 8. O Robocode em si roda nele, mas o compilador embutido no editor
    (Eclipse ECJ) exige Java 17+ e falha com UnsupportedClassVersionError. Este
    script procura uma JVM 17+ e usa ela, entao o editor compila normalmente.

    Uso:  .\run.ps1
#>

$ErrorActionPreference = 'Stop'
$RobocodeHome = 'C:\robocode'

function Get-JavaMajor([string]$javaExe) {
    try {
        $raw = & $javaExe -version 2>&1 | Select-Object -First 1
        if ($raw -match '"(\d+)\.(\d+)') {
            # "1.8.0_401" -> 8 ; "21.0.9" -> 21
            if ($matches[1] -eq '1') { return [int]$matches[2] }
            return [int]$matches[1]
        }
    } catch { }
    return 0
}

$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
$candidates += Get-ChildItem 'C:\Program Files\Java','C:\Program Files\Eclipse Adoptium','C:\Program Files\JetBrains' `
                 -Filter 'java.exe' -Recurse -Depth 4 -ErrorAction SilentlyContinue |
               Select-Object -ExpandProperty FullName
$onPath = Get-Command java.exe -ErrorAction SilentlyContinue
if ($onPath) { $candidates += $onPath.Source }

$java = $null
foreach ($c in $candidates) {
    if ($c -and (Test-Path $c) -and (Get-JavaMajor $c) -ge 17) { $java = $c; break }
}

if (-not $java) {
    Write-Warning 'Nenhuma JVM 17+ encontrada. O Robocode vai abrir, mas o editor nao compila.'
    $java = 'java'
}

Write-Host "java   : $java" -ForegroundColor DarkGray

$javaArgs = @(
    '-cp', "$RobocodeHome\libs\*"
    '-Xmx1024M'
    '-XX:+IgnoreUnrecognizedVMOptions'
    '-Djava.security.manager=allow'
    '--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED'
    '--add-opens=java.base/java.lang.reflect=ALL-UNNAMED'
    '--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED'
    '--add-opens=java.desktop/sun.awt=ALL-UNNAMED'
    'robocode.Robocode'
) + $args

Push-Location $RobocodeHome
try { & $java @javaArgs }
finally { Pop-Location }
