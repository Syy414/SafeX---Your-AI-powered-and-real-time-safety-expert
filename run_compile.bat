@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo Using JAVA_HOME: %JAVA_HOME%
"%JAVA_HOME%\bin\java" -version
call .\gradlew.bat :app:compileDebugKotlin --stacktrace > build_log.txt 2>&1
if errorlevel 1 echo Gradle failed with errorlevel %errorlevel%
