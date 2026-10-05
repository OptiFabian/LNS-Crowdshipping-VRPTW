@echo off
rem Build and run the LNS. Usage: run.bat [--customers N] [--routes] [instance ...]   e.g. run.bat c107 c108
rem With no instance names, runs the default set c107, c108, c109.
rem Optional: set SEED=42 before running for a reproducible run.
setlocal
cd /d "%~dp0"
rem Use the JDK in JAVA_HOME if set; otherwise javac/java must be on PATH.
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
if not exist build mkdir build
javac -d build *.java || exit /b 1
if defined SEED (
  java -Dseed=%SEED% -cp build App %*
) else (
  java -cp build App %*
)
