# TCN — Robocode

Robô de competição para o Campeonato Robocode (Fundação Valeparaibana de Ensino / Colégios UniVap).

## A estratégia em uma frase

O campeonato não premia quem mata mais, premia quem **não morre cedo**. Em uma arena
de 16 robôs onde só metade avança, o TCN trata sobrevivência como objetivo primário e
dano como secundário — e inverte essa prioridade sozinho quando a arena esvazia e vira duelo.

## Condições confirmadas pela comissão

- **Arena 800x600**, todas as configurações padrão do Robocode (pode aumentar um pouco).
- **Entrega: apenas o código-fonte**, colado no editor pelo juiz. Nada de `.jar`.
- **Sem acesso** aos robôs das outras equipes.

## Formato do campeonato

| Fase | Robôs na arena | Rodadas | Corte |
|------|----------------|---------|-------|
| 1 | 16 | 5 | metade avança |
| 2 | 8 | 5 | metade avança |
| 3 | 4 | 5 | metade avança |
| Semi / Final | 2 | 10 | pontuação direta |

O robô carrega dois cérebros e troca conforme o número de inimigos vivos (`getOthers()`).

## Resultados medidos

Sempre na condição real do regulamento: 20 batalhas independentes por fase,
5 rodadas (10 na final), arena 800x600.

| Fase | 1º lugar | Classifica | Pior colocação |
|---|---|---|---|
| Fase 1 (16 robôs) | 18/20 (90%) | 20/20 (100%) | 2º |
| Fase 2 (8 robôs) | 20/20 (100%) | 20/20 | 1º |
| Fase 3 (4 robôs) | 20/20 (100%) | 20/20 | 1º |
| Final vs `T67` (competidor real) | 20/20 (100%) | — | 1º |
| Final vs geração 1 congelada | 18/20 (90%) | — | 2º |

Turnos perdidos: **zero** (30 numa rodada desclassificam).

### Como medir sem se enganar

**O erro mais caro do projeto:** todo o ajuste do duelo foi validado em batalhas de
400 rodadas, mas o campeonato tem 5 e 10. O robô aprende entre rodadas — mapa de
perigo e estatísticas de mira se acumulam — então em 400 rodadas ele fica imbatível
e em 10 mal saiu do palpite inicial. Resultado: o robô **perdia** 13 a 17 na condição
real enquanto "vencia" 256 a 144 na condição de teste.

A regra que ficou: **muitas batalhas curtas, nunca uma batalha longa.** É o que o
`bench.ps1` faz.

Corolário prático: defesas que funcionam **desde o primeiro turno** valem mais que
defesas que precisam aprender. Foi assim que a distância de órbita virou o ajuste
mais importante do duelo (de 374px para 485px levou o placar de 13-17 para 27-3).

## Como funciona

| Peça | Arquivo | O que faz |
|---|---|---|
| Campo de risco | `Movement.java` | Espalha 96 pontos candidatos e vai para o de menor custo. Pune proximidade, repetir ângulo, fogo cruzado e cantos. |
| Canhão | `Gun.java` | Quatro miras em paralelo (direta, linear, circular, GuessFactor). Mede qual acerta cada inimigo e usa a vencedora. |
| Wave surfing | `Surf.java` | Simula a física do próprio robô nos dois sentidos e desvia para onde aquele atirador menos acerta. Mapa de perigo segmentado em 9 faixas. |
| Defesa por projétil | `Shield.java` | Sombra e interceptação. **Desligadas** — ver abaixo. |

## Hipóteses testadas e rejeitadas

Registradas porque saber o que **não** funciona vale tanto quanto o que funciona.

| Ideia | Resultado |
|---|---|
| Parry ativo (abater o projétil no ar) | 16/20 contra 18/20. Rejeitado também na condição curta. |
| Sombra de projétil | 16/20 contra 18/20. |
| Parar no wave surfing (3ª opção) | 35/65 contra a versão sem. Entrega o robô para mira direta. |
| Alvo por fragilidade em vez de proximidade | 67 rodadas contra 90. Os 50 pontos de survival vão para todos os vivos, não para quem matou. |
| Baixar `MIN_SAMPLES` dos canhões virtuais | 17/20 contra 19/20. Com 4 amostras a taxa de acerto é ruído. |
| Perfil "sobrevivência máxima" no melee | −25%, e sobrevive *menos* — o peso de fuga encurrala o robô. |
| Raios de movimento maiores ou menores | Empate dentro do ruído nos dois sentidos. |

## Como buildar

```powershell
.\build.ps1
```

Compila, instala em `C:\robocode\robots\tcn`, e gera `dist/TCN.java` — o **arquivo
único sem declaração de pacote**, que é o entregável.

## Como medir

```powershell
.\bench.ps1 -Preset melee16 -Battles 20
```

Roda 20 batalhas independentes de 5 rodadas e reporta taxa de 1º lugar e de
classificação. Use `-Preset duel -Rounds 10` para a final.

Para assistir: `.\battle.ps1 -Preset melee16 -Rounds 3 -Display`

## Entrega no dia

Levar o conteúdo de **`dist/TCN.java`**. É um arquivo único, sem `package`, com todas
as classes dentro — o juiz cola no editor do Robocode, salva como `TCN.java` e compila.
Testado de ponta a ponta.

Se o editor do Robocode reclamar de compilador na máquina do evento, o `fix-compiler.ps1`
resolve (o erro acontece quando a máquina só tem JRE, que não traz `javac`).

## Estrutura

```
build.ps1          compila, instala e gera o entregável
battle.ps1         roda uma batalha (use -Display para assistir)
bench.ps1          mede N batalhas na condição do regulamento
run.ps1            abre o Robocode com uma JVM moderna
fix-compiler.ps1   conserta o compilador do editor do Robocode

src/tcn/    o robô de competição
src/lev/    geração 1 congelada — régua fixa entre gerações
src/spar/   adversários de treino (ver abaixo)
src/teste/  T67 — competidor real
docs/       regulamento, manual e material da disciplina
tools/      instalador do Robocode usado no projeto
```

### Adversários de treino (`src/spar/`)

| Robô | O que modela |
|---|---|
| `Hunter` | Persegue e fecha distância, mira linear |
| `Circler` | Orbita a distância fixa, mira circular |
| `Surfer` | Inverte o sentido ao detectar disparo |
| `Sniper` | Fica longe e atira de longe |
| `Rammer` | Vai para cima e colide |
| `WallsPro` | **Ameaça declarada:** movimento do `Walls` com mira GuessFactor |
| `WallsDodge` | Cenário pessimista: o `WallsPro` que também esquiva |

`src/teste/T67.java` é o único adversário **real** do conjunto — publicado pelo
autor em [github.com/thales-biondi12/Robocode](https://github.com/thales-biondi12/Robocode).
Os demais são reconstruções.

## Documentação

| Arquivo | Conteúdo |
|---|---|
| [Regulamento](docs/Regulamento_Robocode.pdf) | Fases, rodadas, critérios de classificação e desempate, conduta |
| [Manual](docs/ITL60801-Robocode-Manual.pdf) | Física do jogo, pontuação, API, categorias de tamanho de código |
| [Vídeo](docs/Video_Sobre_Robocode.txt) | Link do vídeo da disciplina |

Regras extraídas desses documentos que moldaram decisões do projeto:

- **Pontuação:** cada morte na arena dá 50 pontos a **todos** os sobreviventes; quem
  mata leva 20% do dano causado como bônus; dano de tiro vale 1 ponto por ponto de dano.
- **30 turnos perdidos numa rodada desclassificam** o robô — daí a verificação a cada build.
- **Classificação por pontuação total**, não por vitórias — é o que o `bench.ps1` mede.
- Sem restrição de tamanho de código (categoria Megabots).

## Limitação conhecida

O `T67` é o **único** competidor real no conjunto de teste. Os outros adversários são
arquétipos que eu mesmo escrevi. Um oponente com mira preditiva seria mais perigoso
que qualquer coisa contra a qual o robô foi calibrado.
