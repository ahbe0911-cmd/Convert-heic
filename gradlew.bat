@echo off
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed. Open the project in Android Studio or run the GitHub Actions workflow.
exit /b 1
