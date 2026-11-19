@echo off
rem หาที่อยู่ Android SDK: ANDROID_HOME > ANDROID_SDK_ROOT > ที่ติดตั้งปกติของ Android Studio
set "SDK=%LOCALAPPDATA%\Android\Sdk"
if defined ANDROID_SDK_ROOT set "SDK=%ANDROID_SDK_ROOT%"
if defined ANDROID_HOME set "SDK=%ANDROID_HOME%"
set "ADB=%SDK%\platform-tools\adb.exe"
set "EMULATOR=%SDK%\emulator\emulator.exe"
if not exist "%ADB%" (
  echo ไม่พบ adb ที่ %ADB%
  echo ติดตั้ง Android Studio ก่อน หรือตั้งค่า ANDROID_HOME
  pause
  exit /b 1
)
exit /b 0
