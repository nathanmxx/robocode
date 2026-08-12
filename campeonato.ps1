#Requires -Version 5.1
<#
    Simula o campeonato inteiro, na ordem e nas regras do regulamento.

      Fase 1   16 robos, 5 rodadas, passam os 8 primeiros
      Fase 2    8 robos, 5 rodadas, passam os 4 primeiros
      Fase 3    4 robos, 5 rodadas, passam os 2 primeiros
      Final     2 robos, 10 rodadas, quem tiver mais pontos leva

    Classificacao e por pontuacao total, que e como o Robocode ordena a tabela
    de resultados. Se o robo cair fora em alguma fase, a simulacao para ali,
    igual ao campeonato de verdade.

    Uso:
      .\campeonato.ps1              # rapido, sem interface
      .\campeonato.ps1 -Display     # abre a janela para assistir cada fase
#>
param(
    [switch]$Display,
    [int]$Tps = 40,
    [string]$Me = 'tcn.TCN*'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
$resultsFile = Join-Path $root 'build\results.txt'
$meLabel = $Me.TrimEnd('*')

$fases = @(
    @{ Nome = 'Fase 1';  Preset = 'melee16'; Rodadas = 5;  Passam = 8 },
    @{ Nome = 'Fase 2';  Preset = 'melee8';  Rodadas = 5;  Passam = 4 },
    @{ Nome = 'Fase 3';  Preset = 'melee4';  Rodadas = 5;  Passam = 2 },
    @{ Nome = 'Final';   Preset = 'duel';    Rodadas = 10; Passam = 1 }
)

$desfecho = 'nao concluiu'

Write-Host ''
Write-Host '  CAMPEONATO ROBOCODE' -ForegroundColor Cyan
Write-Host '  ===================' -ForegroundColor Cyan
Write-Host ''

foreach ($fase in $fases) {
    if (Test-Path $resultsFile) { Remove-Item $resultsFile -Force }

    $args = @{ Preset = $fase.Preset; Rounds = $fase.Rodadas; Width = 800; Height = 600; Me = $Me }
    if ($Display) { $args.Display = $true; $args.Tps = $Tps }

    Write-Host ("  {0} ... " -f $fase.Nome) -NoNewline
    & (Join-Path $root 'battle.ps1') @args *>$null

    if (-not (Test-Path $resultsFile)) {
        Write-Host 'sem resultado' -ForegroundColor Red
        if ($Display) { Write-Host '  (feche a janela do Robocode para a proxima fase)' -ForegroundColor DarkGray }
        break
    }

    # A tabela do Robocode ja vem ordenada por pontuacao total.
    $posicao = 0; $pontos = 0; $total = 0
    foreach ($linha in Get-Content $resultsFile) {
        if ($linha -match '^\s*(\d+)(?:st|nd|rd|th):\s*(\S+)\s+(\d+)\s+\((\d+)%\)') {
            $total++
            if ($matches[2] -like "*$meLabel*") {
                $posicao = [int]$matches[1]
                $pontos  = [int]$matches[3]
            }
        }
    }

    if ($posicao -eq 0) { Write-Host 'robo nao encontrado' -ForegroundColor Red; break }

    $passou = $posicao -le $fase.Passam
    $cor = if ($passou) { 'Green' } else { 'Red' }
    $texto = if ($fase.Nome -eq 'Final') {
        if ($passou) { 'CAMPEAO' } else { 'vice-campeao' }
    } else {
        if ($passou) { 'classificou' } else { 'ELIMINADO' }
    }

    Write-Host ("{0}o de {1}, {2} pontos, {3}" -f $posicao, $total, $pontos, $texto) -ForegroundColor $cor

    if (-not $passou) { $desfecho = $fase.Nome; break }
    if ($fase.Nome -eq 'Final') { $desfecho = 'CAMPEAO' }
}

# Write-Output e nao Write-Host: o Write-Host desenha na tela mas nao entra no
# fluxo de saida, entao quem chama este script num laco para contar titulos
# recebia vazio e achava que nunca ganhou.
Write-Output "RESULTADO=$desfecho"
Write-Host ''
