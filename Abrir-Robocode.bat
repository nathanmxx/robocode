@echo off
REM ---------------------------------------------------------------------------
REM  Abre o Robocode com o JDK, e nao com o JRE.
REM
REM  Clicando no atalho normal, o Robocode sobe com o Java do PATH. Se ali so
REM  houver JRE, ele avisa "Java Compiler (javac) does not exists or cannot
REM  compile" e o editor nao compila nada, porque JRE nao traz o compilador.
REM
REM  Este arquivo poe o JDK na frente do PATH e sobe o Robocode com ele. De
REM  clique duplo aqui em vez do atalho.
REM
REM  Depende do link C:\robocode\jdk, criado por fix-compiler.ps1.
REM ---------------------------------------------------------------------------

if not exist "C:\robocode\jdk\bin\java.exe" (
    echo.
    echo  Falta o link para o JDK em C:\robocode\jdk
    echo  Rode primeiro o fix-compiler.ps1 que esta nesta mesma pasta.
    echo.
    pause
    exit /b 1
)

cd /d C:\robocode
set "PATH=C:\robocode\jdk\bin;%PATH%"

start "" "C:\robocode\jdk\bin\javaw.exe" ^
  -cp "C:\robocode\libs\*" ^
  -Xmx1024M ^
  -XX:+IgnoreUnrecognizedVMOptions ^
  -Djava.security.manager=allow ^
  --add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED ^
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED ^
  --add-opens=java.desktop/javax.swing.text=ALL-UNNAMED ^
  --add-opens=java.desktop/sun.awt=ALL-UNNAMED ^
  robocode.Robocode
