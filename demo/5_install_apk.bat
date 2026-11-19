@echo off
chcp 65001 >nul
title SAFE - ติดตั้งแอปลงมือถือจำลองหรือมือถือที่เสียบสาย
call "%~dp0_android_paths.bat" || exit /b 1
set "APK="
for /f "delims=" %%f in ('dir /b /o:d "%~dp0*.apk" 2^>nul') do set "APK=%~dp0%%f"
if not defined APK (
  echo วางไฟล์ .apk ไว้ในโฟลเดอร์ demo ก่อน
  echo ดาวน์โหลดจาก GitHub: Actions - Android CI รอบล่าสุด - Artifacts - safe-debug-apk
  pause
  exit /b 1
)
echo กำลังติดตั้ง %APK%
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo ติดตั้งไม่สำเร็จ - เปิดมือถือจำลองก่อน หรือเสียบสายมือถือที่เปิด USB debugging
  pause
  exit /b 1
)
echo ติดตั้งเสร็จแล้ว
"%SystemRoot%\System32\timeout.exe" /t 4 >nul
