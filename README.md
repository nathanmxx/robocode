# TCNbots

Robô de combate escrito em Java para o Robocode.

No Robocode você não joga com o tanque, você programa o cérebro dele. Depois que
a batalha começa ninguém toca em nada: o robô precisa enxergar sozinho com o
radar, decidir para onde ir, calcular a mira e escolher a hora de atirar. Tudo
isso acontece dezenas de vezes por segundo, e cada turno dá um tempo limitado
para pensar.

Este projeto foi feito para o Campeonato Robocode dos Colégios UniVap.

## Como o robô pensa

Ele tem dois modos e troca entre eles sozinho, olhando quantos inimigos ainda
estão vivos.

### Arena cheia

Com vários robôs na arena, o problema não é acertar, é não estar no lugar errado.
O robô espalha 96 pontos ao redor dele e dá uma nota de perigo para cada um, e
então vai para o mais seguro. A nota leva em conta:

* estar perto de alguém, principalmente de quem tem muita energia
* ficar no meio de dois inimigos, onde o tiro que erra um acerta você
* encostar em parede, e principalmente em canto, onde some metade das saídas
* continuar na mesma direção em relação a um inimigo, porque aí a mira dele
  continua valendo

Como a nota muda a cada turno, o movimento fica imprevisível sem precisar de
sorteio. Não existe um padrão de fuga para o adversário aprender.

### Um contra um

Quando sobra só um inimigo, fugir deixa de fazer sentido e o jogo vira desviar de
tiro. Aqui entra a parte que eu mais gostei de fazer.

O projétil é invisível, mas dá para saber que ele existe: quando um robô atira,
ele perde energia. Vendo essa queda dá para calcular de onde o tiro saiu, quando
saiu e com que velocidade. Isso desenha um círculo que cresce, e é só descobrir
de que lado desse círculo é mais seguro estar.

Para escolher o lado, o robô simula a própria física do Robocode turno a turno
(aceleração, frenagem e taxa de giro reais) nos dois sentidos possíveis, e vê
onde cada escolha o deixaria. Ele guarda onde cada inimigo já acertou antes e usa
isso para decidir. Como essa memória sobrevive entre as rodadas, ele fica mais
difícil de acertar conforme a batalha avança.

### Mira

O robô calcula quatro miras diferentes ao mesmo tempo:

| Mira | Como funciona |
|---|---|
| Direta | Aponta para onde o inimigo está agora |
| Linear | Assume que ele segue em frente na mesma velocidade |
| Circular | Assume que ele mantém a curva que está fazendo |
| Estatística | Usa onde aquele inimigo costuma estar quando o tiro chega |

Depois de cada tiro ele confere qual das quatro teria acertado e vai anotando.
Com o tempo passa a usar contra cada inimigo aquela que mais acerta nele. Isso
resolve um problema real: mira linear destrói quem anda reto e erra feio em quem
oscila, então não existe uma mira boa para todos.

### Radar

Com a arena cheia o radar gira sem parar, porque perder alguém de vista é pior do
que ver todo mundo com atraso. No um contra um ele trava no inimigo, já que aí
cada turno sem informação é um turno desviando às cegas.

## Como rodar

Precisa do Robocode instalado em `C:\robocode` e de um JDK.

```powershell
.\build.ps1
```

Compila, instala o robô no Robocode e gera `dist/TCNbots.java`, que é o robô
inteiro em um arquivo só.

```powershell
.\battle.ps1 -Preset melee16 -Rounds 5 -Display
```

Roda uma batalha e abre a janela para assistir.

```powershell
.\bench.ps1 -Preset melee16 -Battles 20
```

Roda 20 batalhas seguidas e mostra em quantas o robô ficou em primeiro. Uma
batalha só não diz nada, porque a variação é grande, então eu sempre olho várias.

## Estrutura

```
build.ps1     compila e instala
battle.ps1    roda uma batalha
bench.ps1     roda várias batalhas e resume o resultado
run.ps1       abre o Robocode
fix-compiler.ps1  conserta o compilador do editor do Robocode

src/tcn/      o robô
src/spar/     robôs de treino, com estilos diferentes para eu testar contra
src/lev/      primeira versão do robô, guardada para comparar as gerações
docs/         regulamento e manual do campeonato
tools/        instalador do Robocode
```

Guardar a primeira versão foi uma das coisas mais úteis que eu fiz. Toda vez que
eu mudava alguma coisa, colocava a versão nova para enfrentar a antiga e via se
tinha melhorado de verdade ou se eu só estava achando que sim.

## O robô por dentro

| Arquivo | O que faz |
|---|---|
| `TCN.java` | Junta tudo e decide qual modo usar (vira `TCNbots` no arquivo gerado) |
| `Movement.java` | O movimento da arena cheia |
| `Surf.java` | O desvio de tiro do um contra um |
| `Gun.java` | As quatro miras |
| `Wave.java` | Representa um tiro no ar |
| `Enemy.java` | Tudo que já foi aprendido sobre cada inimigo |
| `Profile.java` | Ajustes que mudam conforme o tamanho da arena e a fase |
| `Shield.java` | Defesa contra projétil (desligada, explicado abaixo) |
| `Util.java` | Contas de geometria e as regras físicas do jogo |

## Coisas que eu tentei e não funcionaram

Deixei no repositório porque saber o que não funciona também conta.

**Atirar no projétil do inimigo.** No Robocode dois tiros que se cruzam se
destroem, então dá para tentar derrubar o tiro no ar. Funciona, mas o canhão é um
só: gastar um tiro nisso significa uns 10 turnos sem revidar, e o desvio normal
já resolveria na maioria das vezes. Fica pior no total. O código está em
`Shield.java`, desligado por uma chave.

**Parar no meio do desvio.** Frear confunde as miras que preveem para onde você
vai, mas entrega você de bandeja para a mira direta. Perdeu feio.

**Mirar no inimigo mais fraco em vez do mais perto.** Parecia esperto porque
matar dá bônus, só que girar o canhão para longe custa mais pontaria do que o
bônus paga.

**Desviar de tiro na arena cheia.** Essa era a que eu mais achava que ia dar
certo, porque na arena cheia o robô só evita posições ruins e nunca reage a um
tiro específico. Fiz cada ponto candidato pagar também pelos projéteis que
estivessem indo naquela direção. Com peso baixo não mudou nada, e com peso alto
o robô ficou fugindo de tiro para dentro de lugares piores e caiu de 24 para 19
primeiros lugares em 30. Parece que com muita gente atirando ao mesmo tempo,
escolher bem a posição já resolve, e reagir a cada tiro atrapalha mais do que
ajuda.

## Usando o robô no Robocode

O `build.ps1` gera `dist/TCNbots.java`, que é o robô inteiro num arquivo só, sem
depender de mais nada. Para rodar ele:

1. Abra o Robocode pelo `Abrir-Robocode.bat`, e não pelo atalho comum, que sobe
   com o Java errado quando a máquina tem só JRE
2. Crie a pasta `C:\robocode\robots\TCNbots`
3. Coloque o `TCNbots.java` dentro dela
4. Compile pelo editor do Robocode
5. Abra Battle, New, e confirme que `TCNbots.TCNbots` aparece na lista

O passo 2 é o que mais dá errado. O arquivo declara `package TCNbots;` na
primeira linha, e em Java o pacote tem que bater com o nome da pasta. Se ficar em
outro lugar, ele compila sem erro nenhum, gera os `.class`, e mesmo assim o
Robocode responde `Can't find` na hora de montar a batalha.

Por isso existe o passo 5. Compilar sem erro não prova nada, o que prova é ver o
nome na lista de robôs.

O robô não precisa de compilador específico: não usa nenhuma construção moderna
da linguagem, e compila igual em JDK 8, 11, 17 e 21.

## Documentação

* [Regulamento](docs/Regulamento_Robocode_Competicao.pdf), com as fases, a pontuação e o formato de entrega
* [Manual](docs/ITL60801-Robocode-Manual.pdf), com a física do jogo e a API

## O que eu aprendi

O maior erro que eu cometi foi testar errado. Eu rodava batalhas de 400 rodadas
para ver se uma mudança tinha sido boa, mas o campeonato tem 5 rodadas. Como o
robô vai aprendendo com o adversário durante a batalha, em 400 rodadas ele fica
ótimo e em 5 mal começou. Uma mudança que parecia excelente estava na verdade
piorando o robô na única condição que importava.

Depois disso passei a medir sempre do jeito que o campeonato acontece, e várias
decisões que eu tinha tomado antes precisaram ser refeitas.
