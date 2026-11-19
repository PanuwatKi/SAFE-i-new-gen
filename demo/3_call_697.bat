@echo off
chcp 65001 >nul
title SAFE - จำลองสายเข้าจาก +697
call "%~dp0_android_paths.bat" || exit /b 1
"%ADB%" emu gsm call +697123456789
if errorlevel 1 (
  echo โทรไม่สำเร็จ - เปิดมือถือจำลองด้วย 2_start_emulator.bat แล้วรอให้หน้าจอขึ้นก่อน
  pause
  exit /b 1
)
echo สายจาก +697 123 456 789 กำลังเข้า ดูที่มือถือจำลอง (SAFE จะเตือนสีแดง)
echo วางสาย: ดับเบิลคลิก 4_end_call.bat หรือกดวางสายบนมือถือจำลอง
"%SystemRoot%\System32\timeout.exe" /t 6 >nul
