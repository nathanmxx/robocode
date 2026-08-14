# Guia de entrega do TCNbots

Equipe **TCN-bots**. Robô entregue como **`TCNbots.java`**.

O nome do robô perde o hífen porque Java não aceita hífen em nome de pacote nem
de classe. **A comissão já confirmou que aceita o nome assim.**

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
        └── TCNbots
             └── TCNbots.java
```

**A pasta precisa se chamar `TCNbots`.** O arquivo declara `package TCNbots;` na
primeira linha, e em Java o pacote tem que bater com o nome da pasta. Se o
arquivo for parar em outro lugar, ele compila normalmente e mesmo assim o
Robocode não encontra o robô na hora de montar a batalha.

---

## No dia: o que fazer

**1. Envie o `TCNbots.java` pelo formulário.** Só esse arquivo. Não mande `.jar`,
não mande pasta compactada.

**2. Se pedirem para carregar na máquina oficial**, a estrutura acima é o que
precisa existir. Confira com quem estiver operando que a pasta se chama
`TCNbots`.

**3. Antes de dar a entrega por concluída**, peça para abrir *Battle → New* e
confirmar que **`TCNbots.TCNbots`** aparece na lista de robôs.

Esse último passo é o único que realmente prova que deu certo. Compilar sem erro
não prova nada: já aconteceu de compilar, gerar tudo, e o robô não existir para
o Robocode porque estava na pasta errada.

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
`C:\robocode\robots\TCNbots\TCNbots.java` e que a primeira linha do arquivo é
`package TCNbots;`.

---

## Pergunta em aberto para a comissão

**O regulamento se contradiz na Fase 2.** O texto diz 16 equipes em 2 arenas de
8, com Top 4 avançando. A tabela logo abaixo diz 18 equipes em 2 arenas de 9,
com Top 5. Qual vale?

Não muda nada do que precisa ser feito na entrega, e o robô se adapta sozinho ao
número de adversários. É só para não haver surpresa na hora.

---

## Contexto do robô

Um arquivo só, todas as classes dentro, sem dependência externa. Compila em
qualquer JDK 8 ou superior. Nos testes ele fica em primeiro na maioria das
simulações das quatro fases.
