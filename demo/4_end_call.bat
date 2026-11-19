@echo off
chcp 65001 >nul
call "%~dp0_android_paths.bat" || exit /b 1
"%ADB%" emu gsm cancel +697123456789
echo วางสายแล้ว
"%SystemRoot%\System32\timeout.exe" /t 3 >nul
