@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Ask IntelliJ (Code Trace Tree plugin) to select trace points by id.
REM With exactly one valid id, the IDE also navigates to the source location.

if "%~1"=="" (
  echo Usage: %~nx0 ^<trace-point-id^> [trace-point-id...] 1>&2
  exit /b 1
)

set "START=%CD%"
pushd "%START%" >nul 2>&1
set "CUR=%CD%"
popd >nul 2>&1

set "PROJECT_ROOT="
:find_root
if exist "%CUR%\.idea\" goto found_root
if exist "%CUR%\.vscode\" goto found_root
if exist "%CUR%\.git\" goto found_root
if exist "%CUR%\.git" goto found_root
for %%I in ("%CUR%\..") do set "PARENT=%%~fI"
if /I "%PARENT%"=="%CUR%" goto no_root
set "CUR=%PARENT%"
goto find_root

:found_root
set "PROJECT_ROOT=%CUR%"
goto after_root

:no_root
echo ERROR: could not locate project root from %START% 1>&2
exit /b 1

:after_root
if not exist "%PROJECT_ROOT%\.idea\" mkdir "%PROJECT_ROOT%\.idea"
set "REQUEST=%PROJECT_ROOT%\.idea\code-trace-tree.select-request"

> "%REQUEST%" (
  for %%A in (%*) do (
    echo %%~A
  )
)

echo wrote=%REQUEST%
echo IDE should select the listed trace points if the project is open with the plugin.
exit /b 0
