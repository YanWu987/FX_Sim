@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
if not exist java\out mkdir java\out
set "FILES="
for /r "java\src" %%f in (*.java) do set "FILES=!FILES! "%%f""
javac -encoding UTF-8 -d java\out !FILES!
if errorlevel 1 (
  echo.
  echo [BUILD FAILED] see errors above.
  pause
  exit /b 1
)
echo [OK] compiled to java\out
exit /b 0
