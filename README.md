# Leviathan — Robocode

Robô de competição para o Campeonato Robocode (Fundação Valeparaibana de Ensino / Colégios UniVap).

## A estratégia em uma frase

O campeonato não premia quem mata mais, premia quem **não morre cedo**. Em uma arena
de 16 robôs onde os 8 primeiros a morrer são eliminados, o Leviathan trata sobrevivência
como objetivo primário e dano como objetivo secundário — e inverte essa prioridade
sozinho quando a arena esvazia e vira duelo.

## Formato do campeonato (do regulamento)

| Fase | Robôs na arena | Rodadas | Corte |
|------|----------------|---------|-------|
| 1 | 16 | 5 | metade avança |
| 2 | 8 | 5 | metade avança |
| 3 | 4 | 5 | metade avança |
| Semi / Final | 2 | 10 | pontuação direta |

Consequência de projeto: o robô precisa ser **excelente em melee** (fases 1–3) e
**excelente em duelo** (semifinal e final). São dois problemas diferentes, então o
Leviathan carrega dois cérebros e troca conforme o número de inimigos vivos.

## Como buildar

```powershell
.\build.ps1
```

Compila `src/` e instala as classes em `C:\robocode\robots\nx`, de onde o Robocode as
lê direto — abrir o Robocode e o robô já aparece na lista. Também gera `dist/leviathan.jar`.

O bytecode é gerado com alvo **Java 8** de propósito: o `.class` carrega em qualquer JVM 8+,
então o robô funciona na máquina oficial do evento seja qual for a versão de Java lá instalada.

## Como testar

```powershell
.\battle.ps1 -Preset melee16 -Rounds 5
.\battle.ps1 -Preset duel -Rounds 10
```

Roda batalhas sem interface (bem mais rápido) e imprime a tabela de pontuação.
Use `-Display` para assistir.

## Estrutura

```
src/nx/     robô de competição
src/spar/   bots de treino — arquétipos do que os adversários provavelmente vão usar
docs/       regulamento e manual
build.ps1   compila e instala
battle.ps1  roda batalhas headless
```

## Entrega no dia

O que carregar na máquina oficial: `dist/leviathan.jar` (ou a pasta `nx/` com os `.class`).
