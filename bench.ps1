#Requires -Version 5.1
<#
    Mede o robo na condicao REAL do campeonato.

    POR QUE EXISTE: batalha longa mede o robo maduro, e o campeonato so ve o robo
    recem-nascido. O robo aprende entre rodadas - mapa de perigo e estatisticas de
    mira se acumulam - entao em 400 rodadas ele fica imbativel e em 10 mal saiu do
    palpite inicial. Ajustar em batalha longa ja fez o duelo ser calibrado para um
    regime que nunca acontece: o robo PERDIA 13 a 17 na condicao da final enquanto
    "vencia" 256 a 144 em 400 rodadas.

    A regra daqui em diante: muitas batalhas curtas, nunca uma batalha longa.

    Uso:
      .\bench.ps1 -Preset melee16 -Battles 20            # Fase 1: 5 rodadas
      .\bench.ps1 -Preset duel -Battles 30 -Rounds 10    # final: 10 rodadas
      .\bench.ps1 -Preset duel -Opponent 'lev.Leviathan*' # contra a regua fixa
#>
param(
    [ValidateSet('melee16','melee8','melee4','duel','sparring','samples')]
    [string]$Preset = 'melee16',

    # Quantas batalhas independentes. Cada uma reinicia o aprendizado, que e
    # exatamente o que acontece a cada fase do campeonato.
    [int]$Battles = 20,

    # Rodadas por batalha. O regulamento usa 5 nas fases 1 a 3 e 10 na
    # semifinal e final.
    [int]$Rounds = 0,

    [string]$Opponent,
    [string]$Me = 'tcn.TCN*',
    [int]$Width  = 0,
    [int]$Height = 0
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition

# Rodadas e criterio de classificacao conforme o regulamento.
if ($Rounds -le 0) { $Rounds = if ($Preset -eq 'duel') { 10 } else { 5 } }
$advance = switch ($Preset) {
    'melee16' { 8 } 'melee8' { 4 } 'melee4' { 2 } default { 1 }
}

$battleArgs = @{ Preset = $Preset; Rounds = $Rounds }
if ($Opponent)     { $battleArgs.Opponent = $Opponent }
if ($Me)           { $battleArgs.Me = $Me }
if ($Width  -gt 0) { $battleArgs.Width  = $Width }
if ($Height -gt 0) { $battleArgs.Height = $Height }

$resultsFile = Join-Path $root 'build\results.txt'
$meLabel = $Me.TrimEnd('*')

$ranks = @(); $scores = @(); $shares = @()
$failed = 0

Write-Host "$Preset : $Battles batalhas de $Rounds rodadas" -ForegroundColor Cyan

for ($i = 1; $i -le $Battles; $i++) {
    if (Test-Path $resultsFile) { Remove-Item $resultsFile -Force }
    # *> e nao 2>: o battle.ps1 usa Write-Host, que escreve no stream de
    # informacao e escapa do redirecionamento de erro.
    & (Join-Path $root 'battle.ps1') @battleArgs *>$null

    if (-not (Test-Path $resultsFile)) { $failed++; continue }

    $found = $false
    foreach ($line in Get-Content $resultsFile) {
        if ($line -match '^\s*(\d+)(?:st|nd|rd|th):\s*(\S+)\s+(\d+)\s+\((\d+)%\)') {
            if ($matches[2] -like "*$meLabel*") {
                $ranks  += [int]$matches[1]
                $scores += [int]$matches[3]
                $shares += [int]$matches[4]
                $found = $true
            }
        }
    }
    if (-not $found) { $failed++ }

    Write-Host '.' -NoNewline -ForegroundColor DarkGray
}
Write-Host ''

if ($ranks.Count -eq 0) { throw 'nenhuma batalha produziu resultado' }

$n      = $ranks.Count
$first  = ($ranks | Where-Object { $_ -eq 1 }).Count
$passed = ($ranks | Where-Object { $_ -le $advance }).Count
$worst  = ($ranks | Measure-Object -Maximum).Maximum
$avgPts = [math]::Round(($scores | Measure-Object -Average).Average)
$avgShr = [math]::Round(($shares | Measure-Object -Average).Average, 1)

Write-Host ''
Write-Host "1o lugar       : $first/$n  ($([math]::Round(100*$first/$n))%)" -ForegroundColor Green
if ($advance -gt 1) {
    Write-Host "classificou    : $passed/$n  ($([math]::Round(100*$passed/$n))%)  [top $advance]" -ForegroundColor Green
}
Write-Host "pontos (media) : $avgPts"
Write-Host "fatia (media)  : $avgShr%"
Write-Host "pior colocacao : $worst"
if ($failed -gt 0) { Write-Warning "$failed batalha(s) sem resultado" }
