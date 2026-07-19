@echo off
setlocal
cd /d "%~dp0"

call gradlew.bat packageWindowsPortable
if errorlevel 1 (
    echo.
    echo Nao foi possivel montar o pacote do Windows.
    pause
    exit /b 1
)

echo.
echo Pacote criado em: build\packages\windows
pause
