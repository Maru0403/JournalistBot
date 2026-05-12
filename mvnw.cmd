@REM Maven Wrapper Script for Windows
@REM Licensed to the Apache Software Foundation (ASF)

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "__MVNW_ARG0_NAME__=%~nx0")
@SET @@FAIL_FAST=
@SET MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
@IF "%MAVEN_PROJECTBASEDIR%"=="" SET "MAVEN_PROJECTBASEDIR=%~dp0"

@SET MAVEN_WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar
@SET MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties

@IF NOT EXIST "%MAVEN_WRAPPER_JAR%" (
  @ECHO Downloading Maven Wrapper JAR...
  @powershell -Command "&{"^
    "$webclient = new-object System.Net.WebClient;"^
    "$url = ((get-content \"%MAVEN_WRAPPER_PROPERTIES%\" | grep wrapperUrl) -split '=',2)[1];"^
    "$webclient.DownloadFile($url, \"%MAVEN_WRAPPER_JAR%\");"^
    "}"
)

@"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% ^
  -classpath "%MAVEN_WRAPPER_JAR%" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  org.apache.maven.wrapper.MavenWrapperMain %*
