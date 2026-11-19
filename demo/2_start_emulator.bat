@echo off
chcp 65001 >nul
title SAFE - เปิดมือถือจำลอง
call "%~dp0_android_paths.bat" || exit /b 1
if not exist "%EMULATOR%" (
  echo ไม่พบ Android emulator ที่ %EMULATOR%
  echo ติดตั้ง Android Studio และสร้างมือถือจำลองก่อน
  pause
  exit /b 1
)
set "AVD="
for /f "delims=" %%a in ('call "%EMULATOR%" -list-avds 2^>nul') do if not defined AVD set "AVD=%%a"
if not defined AVD (
  echo ยังไม่มีมือถือจำลอง - เปิด Android Studio แล้วสร้างที่ Device Manager ก่อน
  pause
  exit /b 1
)
echo กำลังเปิดมือถือจำลอง %AVD% (ใช้เวลาประมาณ 1 นาที)
start "SAFE emulator" "%EMULATOR%" -avd %AVD%
echo เมื่อหน้าจอมือถือขึ้นแล้ว ดับเบิลคลิก 3_call_697.bat เพื่อจำลองสายเข้า
"%SystemRoot%\System32\timeout.exe" /t 8 >nul
