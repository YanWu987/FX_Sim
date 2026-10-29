@echo off
setlocal
cd /d "%~dp0"
set "JAVA_BIN=%ProgramFiles%\Java\jdk-17\bin"
if exist "%JAVA_BIN%\jpackage.exe" set "PATH=%JAVA_BIN%;%PATH%"

call build.bat
if errorlevel 1 exit /b 1

if not exist build\pkg mkdir build\pkg
jar --create --file build\pkg\fxsim.jar -C java\out .
if errorlevel 1 (
  echo [FAILED] jar
  exit /b 1
)

if exist "dist\FX Sim" rmdir /s /q "dist\FX Sim"
jpackage --type app-image --name "FX Sim" --app-version 1.0 --vendor "FX Sim" ^
  --add-modules "java.base,java.desktop" --input build\pkg --main-jar fxsim.jar ^
  --main-class fxsim.Main --java-options "-Dfile.encoding=UTF-8" ^
  --icon build\fx.ico --dest dist
if errorlevel 1 (
  echo [FAILED] jpackage
  exit /b 1
)
echo [OK] dist\FX Sim\FX Sim.exe
exit /b 0
