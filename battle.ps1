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
    # Turnos por segundo no modo visual. 30 da para acompanhar; 10 mostra a
    # esquiva tiro a tiro; acima de 60 vira borrao.
    [int]$Tps = 30,
    # Coloca a versao de referencia na mesma arena, no lugar de um adversario.
    # Comparacao pareada: as duas versoes enfrentam as mesmas rodadas e os mesmos
    # sorteios de posicao, o que remove parte da variancia do melee.
    [switch]$Paired,
    # Contra qual versao comparar. lev.Leviathan e a geracao 1 congelada, que
    # serve de regua estavel entre gerações.
    [string]$Reference = 'lev.Leviathan*'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RobocodeHome = 'C:\robocode'

# Arquetipos que um competidor com IA provavelmente vai produzir.
# O sufixo '*' e obrigatorio: robos carregados como .class solto no diretorio de
# robos sao "development robots" para o Robocode, e o nome oficial deles leva
# asterisco. Sem ele o motor responde "Can't find".
$spar = @('spar.Hunter*','spar.Circler*','spar.Surfer*','spar.Rammer*','spar.Sniper*',
          'spar.WallsPro*')

# teste.T67 e um COMPETIDOR REAL, publicado pelo autor em
# github.com/thales-biondi12/Robocode. Entra no lugar do sample.MyFirstRobot, que
# era o mais fraco da lista: adversario de verdade vale mais que bot de exemplo.
# Isso muda a linha de base do melee - numeros anteriores a esta troca nao sao
# comparaveis com os de agora.
# spar.WallsPro entrou no lugar do sample.VelociRobot, que era o mais fraco:
# e o modelo da ameaca declarada por uma equipe adversaria (movimento do Walls
# com mira aprimorada). Mantem os 16 robos da Fase 1.
$samp = @('sample.Crazy','sample.Tracker','sample.SpinBot','sample.Walls','sample.RamFire',
          'sample.Corners','sample.TrackFire','sample.Fire','teste.T67*')

# O preset so define o tamanho padrao da arena; -Width/-Height explicitos mandam.
# Isso e o que permite varrer a mesma fase em varias arenas, ja que o regulamento
# nao diz qual tamanho os juizes vao usar.
$explicitSize = $PSBoundParameters.ContainsKey('Width') -or $PSBoundParameters.ContainsKey('Height')

switch ($Preset) {
    'melee16'  { $field = $samp + $spar
                 if (-not $explicitSize) { $Width = 1000; $Height = 1000 } }
    # Fases 2 e 3 recebem os adversarios MAIS FORTES, nao os primeiros da lista:
    # quem chega la e quem sobreviveu a fase anterior. Antes estes presets usavam
    # os mais fracos, o que tornava as fases seguintes mais faceis que a Fase 1 -
    # o oposto do funil real do campeonato.
    'melee8'   { $field = @('teste.T67*','spar.WallsPro*','sample.Walls','sample.SpinBot',
                            'spar.Hunter*','spar.Surfer*','spar.Circler*') }
    'melee4'   { $field = @('teste.T67*','spar.WallsPro*','spar.Surfer*') }
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
    '--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED'
    '--add-opens=java.base/java.lang.reflect=ALL-UNNAMED'
    '--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED'
    '--add-opens=java.desktop/sun.awt=ALL-UNNAMED'
    'robocode.Robocode'
    '-battle', $battleFile
    '-results', $resultsFile
)

if ($Display) {
    # Velocidade de quem esta assistindo, nao de quem esta medindo.
    $javaArgs += '-tps', "$Tps"
} else {
    # headless so aqui: passar isso junto com -Display fazia o Robocode
    # responder "Disabled GUI on headless system" e a janela nunca abrir.
    $javaArgs = @('-Djava.awt.headless=true') + $javaArgs
    $javaArgs += '-nodisplay', '-tps', '10000'
}

# Ao subir a interface, o Robocode chama 'javac' pelo PATH. Se a maquina so tem
# JRE, ele falha com "Cannot run program javac" antes mesmo de abrir a janela.
# Prefixar um JDK no PATH deste processo resolve sem mexer no PATH do sistema.
$jdkBin = Join-Path $RobocodeHome 'jdk\bin'
if (-not (Test-Path (Join-Path $jdkBin 'javac.exe'))) {
    $found = Get-ChildItem 'C:\Program Files\Java','C:\Program Files\Eclipse Adoptium',
                           'C:\Program Files\JetBrains' `
               -Filter 'javac.exe' -Recurse -Depth 5 -ErrorAction SilentlyContinue |
             Select-Object -First 1
    if ($found) { $jdkBin = $found.Directory.FullName }
}
if (Test-Path (Join-Path $jdkBin 'javac.exe')) { $env:PATH = "$jdkBin;$env:PATH" }

Push-Location $RobocodeHome

# 'Stop' volta a 'Continue' apenas aqui. No PowerShell 5.1, redirecionar o stderr
# de um executavel nativo transforma CADA linha em ErrorRecord - entao um aviso
# inofensivo da JVM ("System::setSecurityManager esta obsoleto", que o Java 21
# imprime e o 8 nao) abortava o script no meio da batalha.
$previousPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try   { & java @javaArgs 2>&1 | Where-Object { $_ -match 'SYSTEM|Exception' } | Select-Object -First 20 }
finally { $ErrorActionPreference = $previousPreference; Pop-Location }

if (Test-Path $resultsFile) {
    Write-Host ''
    Get-Content $resultsFile | ForEach-Object {
        if     ($_ -match [regex]::Escape('TCN')) { Write-Host $_ -ForegroundColor Green }
        elseif ($_ -match '^\s*Rank') { Write-Host $_ -ForegroundColor DarkGray }
        else   { Write-Host $_ }
    }
} elseif ($Display) {
    # Em modo visual o Robocode mantem a janela aberta depois da batalha e so
    # grava o placar ao ser fechado. Ausencia de resultado aqui e normal.
    Write-Host 'Feche a janela do Robocode para encerrar.' -ForegroundColor DarkGray
} else {
    Write-Warning "sem arquivo de resultados - a batalha falhou"
}
