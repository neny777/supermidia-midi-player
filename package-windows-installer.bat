@echo off
setlocal
cd /d "%~dp0"

call gradlew.bat packageWindowsInstaller
if errorlevel 1 (
    echo.
    echo Nao foi possivel montar o instalador. Confirme se o WiX Toolset esta instalado.
    pause
    exit /b 1
)

echo.
echo Instalador criado em: build\packages\windows\installer
pause
