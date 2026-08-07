#Requires -Version 5.1
<#
    Roda batalhas headless e imprime a tabela de pontuacao.

    Exemplos:
      .\battle.ps1 -Preset melee16 -Rounds 5      # simula a Fase 1 do campeonato
      .\battle.ps1 -Preset duel    -Rounds 10     # simula a Grande Final
      .\battle.ps1 -Preset sparring -Rounds 5     # so contra os bots de treino
#>
param(
    [ValidateSet('melee16','melee8','duel','sparring','samples')]
    [string]$Preset = 'melee16',
    [int]$Rounds = 5,
    [int]$Width  = 800,
    [int]$Height = 600,
    [string]$Me  = 'nx.Leviathan*',
    [string]$Opponent,
    [switch]$Display
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RobocodeHome = 'C:\robocode'

# Arquetipos que um competidor com IA provavelmente vai produzir.
# O sufixo '*' e obrigatorio: robos carregados como .class solto no diretorio de
# robos sao "development robots" para o Robocode, e o nome oficial deles leva
# asterisco. Sem ele o motor responde "Can't find".
$spar = @('spar.Hunter*','spar.Circler*','spar.Surfer*','spar.Rammer*','spar.Sniper*')
$samp = @('sample.Crazy','sample.Tracker','sample.SpinBot','sample.Walls','sample.RamFire',
          'sample.Corners','sample.TrackFire','sample.Fire','sample.VelociRobot','sample.MyFirstRobot')

switch ($Preset) {
    'melee16'  { $field = $samp + $spar ; $Width = 1000; $Height = 1000 }
    'melee8'   { $field = ($samp[0..2] + $spar[0..3]) }
    'duel'     { $field = @( $(if ($Opponent) { $Opponent } else { 'spar.Surfer*' }) ) }
    'sparring' { $field = $spar }
    'samples'  { $field = $samp }
}

$robots = (@($Me) + $field) -join ','
$count  = ($robots -split ',').Count

$battleFile = Join-Path $root 'build\current.battle'
New-Item -ItemType Directory -Force -Path (Split-Path $battleFile) | Out-Null
@"
#Battle Properties
robocode.battleField.width=$Width
robocode.battleField.height=$Height
robocode.battle.numRounds=$Rounds
robocode.battle.gunCoolingRate=0.1
robocode.battle.rules.inactivityTime=450
robocode.battle.hideEnemyNames=false
robocode.battle.selectedRobots=$robots
"@ | Out-File -FilePath $battleFile -Encoding ascii

$resultsFile = Join-Path $root 'build\results.txt'
if (Test-Path $resultsFile) { Remove-Item $resultsFile -Force }

Write-Host "arena  : $count robos, ${Width}x${Height}, $Rounds rodadas" -ForegroundColor Cyan

$javaArgs = @(
    '-cp', "$RobocodeHome\libs\*"
    '-Xmx1024M'
    '-XX:+IgnoreUnrecognizedVMOptions'
    '-Djava.security.manager=allow'
    '-Djava.awt.headless=true'
    '--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED'
    '--add-opens=java.base/java.lang.reflect=ALL-UNNAMED'
    'robocode.Robocode'
    '-battle', $battleFile
    '-results', $resultsFile
)
if (-not $Display) { $javaArgs += '-nodisplay' ; $javaArgs += '-tps' ; $javaArgs += '10000' }

Push-Location $RobocodeHome
try { & java @javaArgs 2>&1 | Where-Object { $_ -match 'SYSTEM|Exception|Error|error' } | Select-Object -First 20 }
finally { Pop-Location }

if (Test-Path $resultsFile) {
    Write-Host ''
    Get-Content $resultsFile | ForEach-Object {
        if     ($_ -match [regex]::Escape('Leviathan')) { Write-Host $_ -ForegroundColor Green }
        elseif ($_ -match '^\s*Rank') { Write-Host $_ -ForegroundColor DarkGray }
        else   { Write-Host $_ }
    }
} else {
    Write-Warning "sem arquivo de resultados - a batalha falhou"
}
