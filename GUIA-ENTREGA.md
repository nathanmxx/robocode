# Guia de entrega do TCN_bots

Este foi o passo a passo que segui para levar o robô pronto para a máquina do
campeonato, incluindo os problemas que encontrei e como resolvi cada um. Ficou
no repositório como registro de como a entrega foi feita de verdade.

Equipe **TCN-bots**. Robô entregue como **`TCN_bots.java`**.

O hífen vira sublinhado porque Java não aceita hífen em nome de pacote nem de
classe: `package TCN-bots;` não compila. O sublinhado é caractere válido em
identificador Java, então `TCN_bots` é o nome da equipe escrito do jeito mais
próximo possível do original.

Dentro do arquivo existe **uma classe só, `TCN_bots`**, e é ela que estende
`AdvancedRobot`. As auxiliares (`Enemy`, `Gun`, `Movement`, `Surf`, `Wave`,
`Profile`, `Shield`, `Util`) ficam aninhadas dentro dela. Nenhuma outra classe
de topo aparece no arquivo.

---

## O que a competição pede

O item 8 do regulamento diz:

> Deve ser criado um diretório com o mesmo nome do programa, que deve ser o nome
> da equipe. Exemplo: `C:\Robocode\Robots\Equipe1\Equipe1.java`
>
> Deverá ser enviado apenas o arquivo com código fonte em Java (`.java`) no
> formulário.

Traduzindo para o nosso caso, a estrutura tem que ficar assim:

```
C:\Robocode
   └── Robots
        └── TCN_bots
             └── TCN_bots.java
```

**A pasta precisa se chamar `TCN_bots`.** O arquivo declara `package TCN_bots;`
na primeira linha de código, e em Java o pacote tem que bater com o nome da
pasta. Se o arquivo for parar em outro lugar, ele compila normalmente e mesmo
assim o Robocode não encontra o robô na hora de montar a batalha.

---

## No dia: o que fazer

**1. Envie o `TCN_bots.java` pelo formulário.** Só esse arquivo. Não mande `.jar`,
não mande pasta compactada.

**2. Se pedirem para carregar na máquina oficial**, a estrutura acima é o que
precisa existir. Confira com quem estiver operando que a pasta se chama
`TCN_bots`.

**3. Antes de dar a entrega por concluída**, peça para abrir *Battle → New* e
confirmar que **`TCN_bots.TCN_bots`** aparece na lista de robôs.

Esse último passo é o único que realmente prova que deu certo. Compilar sem erro
não prova nada: já aconteceu de compilar, gerar tudo, e o robô não existir para
o Robocode porque estava na pasta errada.

### O nome que aparece no jogo

| Onde | O que aparece |
|---|---|
| *Battle → New*, lista de robôs | `TCN_bots.TCN_bots*` |
| Placar e arena, durante a batalha | `TCN_bots*` |

Na lista o Robocode escreve `pacote.classe`; como os dois se chamam `TCN_bots`,
sai o nome repetido. Na arena ele usa só o nome curto.

**O asterisco não faz parte do nome.** O Robocode marca com `*` todo robô
compilado no editor em vez de empacotado em `.jar` — é a marca de *development
version*. Como o regulamento manda enviar só o `.java`, todo robô do campeonato
vai aparecer assim. Ele só sumiria empacotando o robô (*Robot → Package robot
for upload*), que é justamente o que o regulamento não permite.

---

## Se a máquina der problema

### O Robocode não está instalado

Rode o instalador do pendrive:

```
java -jar robocode-1.11.1-setup.jar
```

Instale em `C:\robocode`. Se o comando não funcionar, a máquina não tem Java, e
aí não há o que fazer sem ajuda do laboratório.

### O editor diz que não encontra compilador

A mensagem é:

```
Java Compiler (javac) does not exists or cannot compile
```

Acontece quando a máquina só tem JRE, que não traz compilador. Rode no PowerShell,
dentro da pasta do pendrive:

```
powershell -ExecutionPolicy Bypass -File fix-compiler.ps1
```

O `-ExecutionPolicy Bypass` faz o script rodar mesmo com o PowerShell bloqueado,
o que é comum em máquina de escola. Depois abra o Robocode pelo
`Abrir-Robocode.bat` do pendrive, não pelo atalho normal.

Se disser que não achou JDK nenhum, a máquina realmente só tem JRE e alguém do
laboratório precisa instalar um JDK.

### O robô não aparece na lista

Quase sempre é a pasta. Confira que o caminho é exatamente
`C:\robocode\robots\TCN_bots\TCN_bots.java` e que a primeira linha de código do
arquivo é `package TCN_bots;`.

---

## Contexto do robô

Um arquivo só, uma classe só de topo (`TCN_bots`), sem dependência externa.
Compila em qualquer JDK 8 ou superior. Terminou o campeonato como campeão,
invicto: 10 vitórias em 10 na final.

### Se perguntarem qual é a classe do robô

É a `TCN_bots`, logo abaixo dos imports, e o cabeçalho do arquivo diz isso:

```java
public class TCN_bots extends AdvancedRobot {
```

`Enemy`, `Gun`, `Movement`, `Surf`, `Wave`, `Profile`, `Shield` e `Util` são
partes do robô — mira, movimento, memória do adversário — e estão declaradas
**dentro** da `TCN_bots`, indentadas, como `static class`. Não são robôs, não são
interfaces e não são classes soltas: são o robô por dentro, e o Robocode nunca
as lista, porque quem estende `AdvancedRobot` é só a `TCN_bots`.

Separar em classes é o que deixa o robô legível; juntar tudo em uma classe
gigante não mudaria nada do comportamento e só dificultaria a leitura.
