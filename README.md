# TCN — Robocode

Robô de competição para o Campeonato Robocode (Fundação Valeparaibana de Ensino / Colégios UniVap).

## A estratégia em uma frase

O campeonato não premia quem mata mais, premia quem **não morre cedo**. Em uma arena
de 16 robôs onde só metade avança, o TCN trata sobrevivência como objetivo primário e
dano como secundário — e inverte essa prioridade sozinho quando a arena esvazia e vira duelo.

## Formato do campeonato (do regulamento)

| Fase | Robôs na arena | Rodadas | Corte |
|------|----------------|---------|-------|
| 1 | 16 | 5 | metade avança |
| 2 | 8 | 5 | metade avança |
| 3 | 4 | 5 | metade avança |
| Semi / Final | 2 | 10 | pontuação direta |

Consequência de projeto: o robô precisa ser **excelente em melee** (fases 1–3) e
**excelente em duelo** (semifinal e final). São dois problemas diferentes, então o TCN
carrega dois cérebros e troca conforme o número de inimigos vivos (`getOthers()`).

## Como funciona

| Peça | Arquivo | O que faz |
|---|---|---|
| Campo de risco | `Movement.java` | Espalha 96 pontos candidatos e vai para o de menor custo. O custo pune proximidade, repetir ângulo, fogo cruzado e cantos. |
| Canhão | `Gun.java` | Quatro miras em paralelo (direta, linear, circular, GuessFactor). Mede qual acerta cada inimigo e usa a vencedora contra ele. |
| Wave surfing | `Surf.java` | Simula a física do próprio robô nos dois sentidos de órbita e desvia para onde aquele atirador menos acerta. |
| Defesa por projétil | `Shield.java` | Sombra e interceptação. **Desligadas por padrão** — ver abaixo. |

O que aprende sobrevive entre rodadas: o Robocode recria a instância a cada rodada mas
mantém o classloader durante a batalha, então campos estáticos atravessam as 5 (ou 10)
rodadas. Na rodada 1 o robô chuta; na rodada 5 já conhece o adversário.

## Resultados medidos

Benchmark de 30 rodadas (5 rodadas de melee é ruído demais para avaliar mudança).
Adversários: 10 sample bots + 5 arquétipos de treino em `src/spar/`.

| Cenário | Resultado |
|---|---|
| Melee 16 (1000x1000) | 1º lugar, 32472 pts contra 20022 do 2º, 25 de 30 rodadas vencidas |
| Melee 8 | 1º lugar, 24% da pontuação total (fatia uniforme seria 12,5%) |
| Duelo vs cada arquétipo | 30–0, entre 90% e 94% da pontuação |
| Turnos perdidos | 0 (30 turnos perdidos numa rodada desclassifica) |

## Sobre o parry

Projéteis se destroem ao se cruzar no Robocode, e as duas defesas estão implementadas e
funcionam. Mas foram medidas e **custam mais do que rendem**:

| Configuração | Dano recebido (4 duelos × 30 rodadas) | % da pontuação |
|---|---|---|
| **Nenhuma** | **1433** | **92,5%** |
| Só interceptação | 1632 | 91,5% |
| Só sombra | 1910 | 90,5% |
| As duas | 1865 | 90,8% |

Dois motivos: o canhão é recurso único (interceptar custa ~10 turnos sem revidar, para
bloquear um tiro que o surfing já desviaria em ~85% das vezes), e projétil é um ponto —
a colisão exige alinhamento quase exato, e o erro da simulação faz a sombra marcar como
seguro um setor que não está.

Ficam atrás de `USE_BULLET_SHADOW` e `USE_ACTIVE_PARRY` em `TCN.java`. Deram lucro contra
os dois arquétipos mais agressivos (Hunter 401→320, Sniper 495→427), então valem
reavaliação se o adversário for preciso a ponto de furar a esquiva.

## Como buildar

```powershell
.\build.ps1
```

Compila `src/`, instala as classes em `C:\robocode\robots\tcn` (o robô já aparece na lista
ao abrir o Robocode), gera `dist/tcn.jar` e a versão de arquivo único `dist/TCN.java`.

O bytecode tem alvo **Java 8** de propósito: o `.class` carrega em qualquer JVM 8+, então
o robô funciona na máquina oficial seja qual for a versão de Java instalada lá.

## Como testar

```powershell
.\battle.ps1 -Preset melee16 -Rounds 30
.\battle.ps1 -Preset duel -Rounds 30 -Opponent 'spar.Surfer*'
```

Batalhas sem interface (bem mais rápido). Use `-Display` para assistir.

## Estrutura

```
src/tcn/    robô de competição
src/spar/   bots de treino — arquétipos do que os adversários provavelmente vão usar
docs/       regulamento e manual
build.ps1   compila, instala, empacota
battle.ps1  roda batalhas headless
```

## Entrega no dia

Carregar `dist/tcn.jar` na máquina oficial. Em `.jar` o robô aparece como `tcn.TCN`;
solto como `.class` apareceria como `tcn.TCN*` (o asterisco marca *development robot*).

Alternativa sem estrutura de pastas: `dist/TCN.java` é o robô inteiro em um arquivo só,
para colar direto no editor do Robocode.

## Em aberto

Três parâmetros de batalha não constam em nenhum documento e mudam o ajuste — vale
confirmar com a comissão: **tamanho da arena** (o padrão do Robocode é 800x600, e 16 robôs
nesse tamanho é um aperto bem diferente de 1000x1000), **gun cooling rate** (padrão 0.1) e
**inactivity time** (padrão 450).
