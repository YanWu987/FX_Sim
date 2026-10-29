@echo off
cd /d "%~dp0"
if not exist java\out\fxsim\Main.class (
  call build.bat
  if errorlevel 1 exit /b 1
)
start "" javaw -Dfile.encoding=UTF-8 -cp java\out fxsim.Main
exit /b 0
