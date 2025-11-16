@echo off
echo ================================================
echo   DemograficoBUDA - Pesquisa Budista
echo   Associacao BUDA - Coleta de Dados
echo ================================================
echo.

echo Compilando e executando o programa...
echo.

cd /d "%~dp0"
call mvn clean javafx:run

pause
