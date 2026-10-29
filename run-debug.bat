@echo off
cd /d "%~dp0"
if not exist java\out\fxsim\Main.class (
  call build.bat
  if errorlevel 1 exit /b 1
)
echo Starting FX Sim ... close this window to quit.
java -Dfile.encoding=UTF-8 -cp java\out fxsim.Main %*
echo.
echo Exited.
pause
