@echo off
setlocal
set LINEKEY_JAVA=java.exe
if defined JAVA_HOME set LINEKEY_JAVA=%JAVA_HOME%\bin\java.exe
"%LINEKEY_JAVA%" -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
