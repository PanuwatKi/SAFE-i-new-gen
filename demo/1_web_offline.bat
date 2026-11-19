@echo off
chcp 65001 >nul
title SAFE - เว็บแอปออฟไลน์
cd /d "%~dp0..\web"
where python >nul 2>nul
if errorlevel 1 (
  echo ไม่พบ Python - ติดตั้งจาก https://www.python.org แล้วลองใหม่
  pause
  exit /b 1
)
start "SAFE web server (ปิดหน้าต่างนี้เพื่อหยุด)" /min python -m http.server 8765 --bind 127.0.0.1
"%SystemRoot%\System32\timeout.exe" /t 2 /nobreak >nul
start "" http://localhost:8765
echo เปิดเว็บแอป SAFE แล้วที่ http://localhost:8765
echo ใช้ได้แม้ไม่มีอินเทอร์เน็ต (ตัวอักษรอาจเป็นฟอนต์ของเครื่องแทน)
echo หยุดเว็บ: ปิดหน้าต่าง "SAFE web server" ที่ย่ออยู่ในแถบงาน
"%SystemRoot%\System32\timeout.exe" /t 8 >nul
