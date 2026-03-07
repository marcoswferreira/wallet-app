REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script
@REM
@REM Required ENV vars:
@REM   JAVA_HOME - location of a JDK home directory
@REM
@REM Optional ENV vars:
@REM   MAVEN_OPTS - parameters passed to the Java VM when running Maven
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "MVN_CMD=mvn") ELSE (SET "MVN_CMD=%__MVNW_ARG0_NAME__%")
@SET "MAVEN_PROJECTBASEDIR=%~dp0"

@IF NOT EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
    @echo No .mvn\wrapper\maven-wrapper.jar found. Cannot run Maven Wrapper.
    @EXIT /B 1
)

@SET JAVA_EXE=java.exe
@IF NOT "%JAVA_HOME%"=="" SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

@"%JAVA_EXE%" ^
    "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
    "-Dmaven.home=%MAVEN_USER_HOME%" ^
    "-Dmaven.wrapper.jar=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" ^
    "-Dmaven.wrapper.properties=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties" ^
    "-classpath" "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" ^
    "org.apache.maven.wrapper.MavenWrapperMain" ^
    %*
