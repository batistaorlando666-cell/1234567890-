@echo off
setlocal

rem -----------------------------------------------------------------------------
rem Minimal Gradle wrapper launcher.
rem NOTE: This launcher requires gradle\wrapper\gradle-wrapper.jar to exist.
rem -----------------------------------------------------------------------------

set "DIR=%~dp0"
set "WRAPPER_JAR=%DIR%gradle\wrapper\gradle-wrapper.jar"

if not exist "%WRAPPER_JAR%" (
  echo ERROR: Missing %WRAPPER_JAR%
  echo Restore it by running: gradle wrapper
  exit /b 1
)

if defined JAVA_HOME (
  set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_CMD=java"
)

"%JAVA_CMD%" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
