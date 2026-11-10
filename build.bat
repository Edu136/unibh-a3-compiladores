@echo off
rem Compila o macsc e roda os testes (gera target\macsc.jar).
rem JDK usado: MACSC_JDK > C:\tools\jdk-21 > JAVA_HOME atual.
rem Os testes de ponta a ponta precisam de NASM e gcc i686 no PATH; se as pastas
rem C:\tools\nasm e C:\tools\mingw32\bin existirem, entram no PATH so durante o build.
setlocal
if exist "C:\tools\jdk-21\bin\javac.exe" set "JAVA_HOME=C:\tools\jdk-21"
if defined MACSC_JDK set "JAVA_HOME=%MACSC_JDK%"
if exist "C:\tools\nasm\nasm.exe" set "PATH=C:\tools\nasm;%PATH%"
if exist "C:\tools\mingw32\bin\gcc.exe" set "PATH=C:\tools\mingw32\bin;%PATH%"
call mvn -B package %*
exit /b %errorlevel%
