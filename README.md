# TCN_bots

Robô de combate escrito em Java para o Robocode.

**Campeão do Campeonato Robocode dos Colégios UniVap, em primeiro lugar nas
quatro fases.**

No Robocode você não joga com o tanque, você programa o cérebro dele. Depois que
a batalha começa ninguém toca em nada: o robô precisa enxergar sozinho com o
radar, decidir para onde ir, calcular a mira e escolher a hora de atirar. Tudo
isso acontece dezenas de vezes por segundo, e cada turno dá um tempo limitado
para pensar.

O robô que competiu está aqui: [`dist/TCN_bots.java`](dist/TCN_bots.java).

## O campeonato

| Fase | Formato | Resultado |
|---|---|---|
| Fase 1 | 9 equipes na arena, 10 rodadas | 1º lugar |
| Fase 2 | 8 equipes na arena, 10 rodadas | 1º lugar |
| Semifinal | 1×1, chaveamento entre os 4 finalistas, 10 rodadas | venceu por 10 a 0 |
| Final | 1×1, 10 rodadas | venceu por 10 a 0 |

As duas primeiras fases foram na arena cheia, com todas as equipes ao mesmo
tempo. A partir da semifinal virou mata-mata: os quatro classificados foram
separados em duas chaves, e as duas batalhas que o robô disputou nesse formato
terminaram 10 a 0.

Os dois formatos pedem coisas opostas, e é por isso que o robô tem dois modos.
Na arena cheia dá pontos quem sobrevive; no um contra um só existe uma forma de
pontuar, que é acertar sem ser acertado.

## Sobre este projeto

As ideias e a maior parte do código são minhas. Usei IA (Claude, da Anthropic)
como apoio ao longo do caminho, principalmente para escrever os scripts de
teste em lote e para revisar trecho por trecho enquanto eu decidia a estratégia.
As decisões de como o robô deveria se comportar, e por quê, foram minhas.

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

## Como eu testava

O campeonato acontece uma vez só, então tudo dependia de conseguir simular as
fases antes. Montei quatro scripts em PowerShell para isso:

| Script | O que faz |
|---|---|
| `build.ps1` | Compila e junta o robô inteiro no arquivo único da entrega |
| `battle.ps1` | Roda uma batalha, com presets para cada formato de fase |
| `bench.ps1` | Roda N batalhas seguidas e conta em quantas o robô ficou em primeiro |
| `campeonato.ps1` | Simula o campeonato inteiro em sequência, parando se o robô for eliminado |
| `run.ps1` | Abre o Robocode para assistir a uma batalha na tela |

O `bench.ps1` é o que mais usei. Uma batalha isolada não diz nada, porque a
variação entre execuções é grande: um robô pior ganha uma batalha por sorte com
facilidade. Vinte batalhas seguidas já mostram se a mudança valeu.

Como adversário eu escrevi robôs de treino com estilos diferentes (`src/spar/`),
cada um explorando uma fraqueza específica — quem persegue, quem orbita, quem
fica na parede, quem atira de longe. Um deles é a reprodução da estratégia que
uma equipe adversária tinha anunciado que ia usar.

Também guardei a primeira versão do robô em `src/lev/`, congelada. Toda vez que
eu mudava alguma coisa, colocava a versão nova para enfrentar a antiga. Foi a
coisa mais útil que eu fiz no projeto: é o que separa "melhorou de verdade" de
"eu achei que tinha melhorado".

## Estrutura

```
src/tcn/      o robô
src/spar/     robôs de treino, com estilos diferentes para eu testar contra
src/lev/      primeira versão do robô, guardada para comparar as gerações
dist/         o robô entregue no campeonato, tudo em um arquivo só
docs/         regulamento e manual do campeonato
```

## O robô por dentro

| Arquivo | O que faz |
|---|---|
| `TCN.java` | Junta tudo e decide qual modo usar (vira `TCN_bots` no arquivo gerado) |
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

## O formato da entrega

O regulamento manda entregar só o `.java`, dentro de uma pasta com o nome da
equipe. O robô é desenvolvido em nove arquivos separados, então o `build.ps1`
junta tudo em `dist/TCN_bots.java`: uma classe pública só, `TCN_bots`, com as
oito auxiliares aninhadas dentro dela.

O aninhamento não é detalhe estético. Na primeira versão as auxiliares ficavam
soltas no mesmo arquivo, o que Java aceita, mas isso colocava `class Enemy` na
primeira linha e a classe do robô lá pela linha 850. Quem abria o arquivo
procurando o nome da equipe encontrava outro nome no topo e concluía que estava
errado — foi exatamente o que aconteceu na conferência da entrega. Com as
auxiliares dentro da classe principal, o arquivo declara uma coisa só e ela
aparece logo abaixo dos imports.

O robô não usa nenhuma construção moderna da linguagem, então compila igual em
JDK 8, 11, 17 e 21, e também no ECJ que acompanha o editor do Robocode. Isso foi
proposital: a máquina do evento era desconhecida.

## Documentação

* [Regulamento](docs/Regulamento_Robocode_Competicao.pdf), com as fases, a pontuação e o formato de entrega
* [Manual](docs/ITL60801-Robocode-Manual.pdf), com a física do jogo e a API
* [Vídeo de introdução ao Robocode](https://youtu.be/8s8BtMYZ2kw)

## O que eu aprendi

O maior erro que eu cometi foi testar errado. Eu rodava batalhas de 400 rodadas
para ver se uma mudança tinha sido boa, mas cada fase do campeonato tem só uma
dezena. Como o robô vai aprendendo com o adversário durante a batalha, em 400
rodadas ele fica ótimo e em 10 mal começou. Uma mudança que parecia excelente
estava na verdade piorando o robô na única condição que importava.

Depois disso passei a medir sempre do jeito que o campeonato acontece, e várias
decisões que eu tinha tomado antes precisaram ser refeitas. Uma delas mudou
completamente o resultado do um contra um: com a medição errada eu tinha
aproximado demais a distância de órbita, e na condição real isso fazia o robô
perder para a própria versão anterior.

## Licença

MIT. Ver [LICENSE](LICENSE).
