# Guia de entrega do TCN

Passo a passo para colocar o robô numa máquina que não é a sua. Feito para ser
seguido com pressa, então cada passo tem como saber se deu certo.

## O que levar no pendrive

| Arquivo | Para que serve |
|---|---|
| `TCN.java` | O robô. É só este arquivo, tem tudo dentro dele |
| `robocode-1.11.1-setup.jar` | Caso o Robocode não esteja instalado |
| `fix-compiler.ps1` | Caso o editor do Robocode não compile |
| `Abrir-Robocode.bat` | Abre o Robocode com o Java certo |

Copie os quatro. Juntos dão menos de 22 MB.

---

## Passo 0: ver o que a máquina tem

Abra o Prompt de Comando (tecla Windows, digite `cmd`, Enter) e rode:

```
java -version
javac -version
```

Três resultados possíveis:

**Os dois responderam.** Melhor caso, pode ir para o Passo 1.

**Só o `java` respondeu.** A máquina tem JRE mas não tem JDK, ou seja, tem como
rodar mas não como compilar. O editor do Robocode vai reclamar. Solução no Passo 3.

**Nenhum respondeu.** Não tem Java. Sem Java o Robocode nem abre. Avise o
professor ou o responsável pelo laboratório, porque isso precisa ser resolvido
antes e provavelmente pede senha de administrador.

---

## Passo 1: o Robocode está instalado?

Procure a pasta `C:\robocode`.

**Se existir**, pule para o Passo 2.

**Se não existir**, instale a partir do pendrive. No Prompt de Comando:

```
java -jar robocode-1.11.1-setup.jar
```

Na tela de instalação, escolha `C:\robocode` como destino. Se a máquina não
deixar escrever ali por falta de permissão, use uma pasta sua, por exemplo
`C:\Users\SEU_USUARIO\robocode`, e lembre desse caminho porque os passos
seguintes mudam junto.

---

## Passo 2: colar o robô

1. Abra o Robocode com **`Abrir-Robocode.bat`** (clique duplo no arquivo do
   pendrive). Se ele reclamar que falta o JDK, vá para o Passo 3 e volte aqui.
2. Menu **Robot**, depois **Editor**.
3. Abra o `TCN.java` num bloco de notas, selecione tudo (Ctrl+A), copie
   (Ctrl+C) e cole no editor do Robocode (Ctrl+V).
4. Menu **File**, depois **Save As**.
5. **Salve como `TCN.java` direto dentro de `C:\robocode\robots`.**

> **O passo 5 é o que mais dá errado, e já deu errado no nosso teste.**
> Não crie pasta nenhuma. O arquivo tem que ficar solto em `robots`.
> Se salvar dentro de uma subpasta, ele compila sem erro, gera os arquivos e
> mesmo assim o Robocode não acha o robô na hora da batalha.

6. Menu **Compiler**, depois **Compile**. Tem que aparecer "Compiled" sem erro.

---

## Passo 3: conferir de verdade

**Compilar sem erro não prova que funcionou.** O que prova é isto:

1. Feche o editor
2. Menu **Battle**, depois **New**
3. Procure **`TCN`** na lista de robôs da esquerda

Se `TCN` aparecer na lista, deu certo. Adicione ele e mais alguns robôs de
exemplo, clique em **Start Battle** e veja ele andando.

Se `TCN` **não** aparecer, o arquivo está no lugar errado. Volte ao Passo 2 e
salve direto em `C:\robocode\robots`, sem subpasta.

---

## Problemas e soluções

### "Java Compiler (javac) does not exists or cannot compile"

A máquina abriu o Robocode com um JRE, que não tem compilador. Rode no Prompt de
Comando, dentro da pasta do pendrive:

```
powershell -ExecutionPolicy Bypass -File fix-compiler.ps1
```

O `-ExecutionPolicy Bypass` está aí porque muitas máquinas de escola bloqueiam
scripts do PowerShell por padrão.

O script procura um JDK na máquina e configura o Robocode para usar ele. Depois
feche e abra o Robocode de novo pelo `Abrir-Robocode.bat`.

Se o script disser que **não achou nenhum JDK**, a máquina realmente só tem JRE.
Nesse caso ninguém consegue compilar robô nenhum ali, o que é um problema do
laboratório e não do nosso robô. Fale com o professor.

### O `Abrir-Robocode.bat` reclama que falta `C:\robocode\jdk`

Rode o `fix-compiler.ps1` primeiro, ele é quem cria esse atalho.

### O Robocode foi instalado em outra pasta

O `Abrir-Robocode.bat` e o `fix-compiler.ps1` procuram em `C:\robocode`. Se você
instalou em outro lugar, abra os dois arquivos no bloco de notas e troque
`C:\robocode` pelo caminho certo.

### `TCN` aparece na lista com um asterisco, tipo `TCN*`

Isso é normal e não é problema. O Robocode marca com asterisco os robôs que
estão como código solto na pasta, em vez de empacotados.

---

## Resumo em três linhas

1. Abrir pelo `Abrir-Robocode.bat`
2. Colar e salvar como `TCN.java` **direto em `C:\robocode\robots`**
3. Conferir que `TCN` aparece em Battle, New
