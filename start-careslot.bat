@echo off
setlocal

set "ROOT=%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_102"
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
set "NODE_EXE=C:\Program Files\nodejs\node.exe"

if not exist "%JAVAC_EXE%" (
    echo Java 8 was not found at:
    echo %JAVA_HOME%
    pause
    exit /b 1
)

if not exist "%NODE_EXE%" (
    echo Node.js was not found at:
    echo %NODE_EXE%
    pause
    exit /b 1
)

if not exist "%ROOT%frontend\node_modules" (
    echo Frontend dependencies are missing.
    echo Run this once in the frontend folder:
    echo npm install --cache .npm-cache
    pause
    exit /b 1
)

pushd "%ROOT%backend"
"%JAVAC_EXE%" -d out src\main\java\app\scheduler\*.java
if errorlevel 1 (
    echo Java compilation failed.
    popd
    pause
    exit /b 1
)
popd

start "CareSlot Java Backend" /D "%ROOT%backend" "%JAVA_EXE%" -cp out app.scheduler.Main
start "CareSlot React Frontend" /D "%ROOT%frontend" "%NODE_EXE%" node_modules\vite\bin\vite.js

rem Use ping for a short delay because timeout fails when this file is started without an interactive stdin.
ping 127.0.0.1 -n 6 >nul
start "" "http://localhost:5173"

endlocal
