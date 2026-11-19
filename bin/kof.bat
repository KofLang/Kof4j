@echo off
rem kof — Kof platform launcher (Windows).
rem Uses the embedded OpenJDK shipped with the distribution when present.
rem Falls back to a system `java` only in development builds.

setlocal
set "KOF_BIN=%~dp0"
set "KOF_HOME=%KOF_BIN%.."

if not exist "%KOF_HOME%\lib\kof.jar" (
    echo kof: distribution incomplete - missing lib\kof.jar
    echo kof: build it with: mvn clean package -DskipTests ^&^& scripts\package.sh
    exit /b 1
)

set "JAVA=java"
set "EMBEDDED=false"
if exist "%KOF_HOME%\jdk\bin\java.exe" (
    set "JAVA=%KOF_HOME%\jdk\bin\java.exe"
    set "EMBEDDED=true"
)

rem kof4shell (CVE-2026-114938): disable the HotSpot Base64 intrinsic (the
rem AVX-512 decode overflow, unauthenticated RCE); KOF_B64_INTRINSICS=1 opts
rem out for a JDK carrying the fix.
rem #1173: no Windows o LAUNCHER fica conservador (o parse de versão em batch é
rem frágil); as JVMs FILHAS usam o version-gate do KofStdio. KOF_B64_INTRINSICS=1
rem liga de volta quando o operador sabe que o JDK tem o fix.
set "B64_FLAG=-XX:-UseBASE64Intrinsics"
if "%KOF_B64_INTRINSICS%"=="1" set "B64_FLAG="

"%JAVA%" %B64_FLAG% -Dkof.install.dir="%KOF_HOME%" -Dkof.embedded.jdk="%EMBEDDED%" -jar "%KOF_HOME%\lib\kof.jar" %*
exit /b %ERRORLEVEL%