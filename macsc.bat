@echo off
rem Executa o compilador macsc com um Java 21.
rem Ordem de escolha do java: MACSC_JAVA > C:\tools\jdk-21 > java do PATH.
setlocal
set "JAVA=java"
if exist "C:\tools\jdk-21\bin\java.exe" set "JAVA=C:\tools\jdk-21\bin\java.exe"
if defined MACSC_JAVA set "JAVA=%MACSC_JAVA%"

rem --exe precisa de nasm e gcc i686; se existirem em C:\tools, entram no PATH so aqui.
if exist "C:\tools\nasm\nasm.exe" set "PATH=C:\tools\nasm;%PATH%"
if exist "C:\tools\mingw32\bin\gcc.exe" set "PATH=C:\tools\mingw32\bin;%PATH%"

if not exist "%~dp0target\macsc.jar" (
    echo macsc.jar nao encontrado. Rode build.bat primeiro. 1>&2
    exit /b 2
)

rem Console em UTF-8 durante a execucao (acentos), restaurado no final.
set "CP_ANTIGA="
for /f "tokens=2 delims=:" %%c in ('chcp') do set "CP_ANTIGA=%%c"
chcp 65001 >nul

"%JAVA%" -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%~dp0target\macsc.jar" %*
set "RC=%errorlevel%"

if defined CP_ANTIGA chcp %CP_ANTIGA: =% >nul
exit /b %RC%
