#Requires -Version 5.1
<#
    Roda batalhas headless e imprime a tabela de pontuacao.

    Exemplos:
      .\battle.ps1 -Preset melee16 -Rounds 5      # simula a Fase 1 do campeonato
      .\battle.ps1 -Preset duel    -Rounds 10     # simula a Grande Final
      .\battle.ps1 -Preset sparring -Rounds 5     # so contra os bots de treino
#>
param(
    [ValidateSet('melee16','melee8','melee4','duel','sparring','samples')]
    [string]$Preset = 'melee16',
    [int]$Rounds = 5,
    [int]$Width  = 800,
    [int]$Height = 600,
    [string]$Me  = 'tcn.TCN*',
    [string]$Opponent,
    [switch]$Display,
    # Coloca a versao de referencia (base.Base) na mesma arena. Comparacao
    # pareada: as duas versoes enfrentam exatamente as mesmas rodadas e os mesmos
    # sorteios de posicao, o que elimina a variancia que torna amostras isoladas
    # de melee inconclusivas.
    [switch]$Paired,
    # Contra qual versao medir. 'base.Base*' e a geracao anterior (alvo movel,
    # para A/B de uma mudanca isolada); 'lev.Leviathan*' e a geracao 1 congelada,
    # que serve de regua estavel ao longo de todas as mudancas.
    [string]$Reference = 'base.Base*'
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

# O preset so define o tamanho padrao da arena; -Width/-Height explicitos mandam.
# Isso e o que permite varrer a mesma fase em varias arenas, ja que o regulamento
# nao diz qual tamanho os juizes vao usar.
$explicitSize = $PSBoundParameters.ContainsKey('Width') -or $PSBoundParameters.ContainsKey('Height')

switch ($Preset) {
    'melee16'  { $field = $samp + $spar
                 if (-not $explicitSize) { $Width = 1000; $Height = 1000 } }
    'melee8'   { $field = ($samp[0..2] + $spar[0..3]) }
    'melee4'   { $field = ($samp[0..0] + $spar[0..1]) }
    'duel'     { $field = @( $(if ($Opponent) { $Opponent } else { 'spar.Surfer*' }) ) }
    'sparring' { $field = $spar }
    'samples'  { $field = $samp }
}

if ($Paired) {
    # Entra no lugar de um adversario, para o total de robos na arena continuar
    # igual ao da fase que estamos simulando.
    $field = @($Reference) + $field[1..($field.Count - 1)]
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
        if     ($_ -match [regex]::Escape('TCN')) { Write-Host $_ -ForegroundColor Green }
        elseif ($_ -match '^\s*Rank') { Write-Host $_ -ForegroundColor DarkGray }
        else   { Write-Host $_ }
    }
} else {
    Write-Warning "sem arquivo de resultados - a batalha falhou"
}
