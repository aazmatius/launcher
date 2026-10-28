@echo off
chcp 65001 >nul
echo Положи app-debug.apk рядом с этим файлом. ADB должен быть в PATH.
set /p IP=IP телевизора: 
adb connect %IP%:5555
adb -s %IP%:5555 install -r app-debug.apk
if errorlevel 1 (echo Установка не удалась & pause & exit /b 1)
set /p H=Сделать TV Home лаунчером по кнопке Home? Штатный лаунчер останется (y/n): 
if /i "%H%"=="y" adb -s %IP%:5555 shell cmd package set-home-activity ru.user.tvhome/.MainActivity
echo.
echo Откат: adb uninstall ru.user.tvhome  (вернётся штатный лаунчер)
pause
