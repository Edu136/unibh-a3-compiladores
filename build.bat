@echo off
rem Compila o macsc e roda os testes (gera target\macsc.jar).
rem JDK usado: MACSC_JDK > C:\tools\jdk-21 > JAVA_HOME atual.
setlocal
if exist "C:\tools\jdk-21\bin\javac.exe" set "JAVA_HOME=C:\tools\jdk-21"
if defined MACSC_JDK set "JAVA_HOME=%MACSC_JDK%"
call mvn -B package %*
exit /b %errorlevel%
