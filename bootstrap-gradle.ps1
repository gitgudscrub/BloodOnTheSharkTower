$ErrorActionPreference = "Stop"

$GradleVersion = "9.6.0"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BootstrapRoot = Join-Path $ProjectRoot ".gradle-bootstrap"
$ZipPath = Join-Path $BootstrapRoot "gradle-$GradleVersion-bin.zip"
$GradleHome = Join-Path $BootstrapRoot "gradle-$GradleVersion"
$GradleBat = Join-Path $GradleHome "bin\gradle.bat"

Write-Host "Blood on the Sharktower - Gradle bootstrap" -ForegroundColor Cyan

# java -version writes its version text to STDERR. Windows PowerShell 5.1 can
# turn redirected native STDERR into a terminating error when ErrorActionPreference
# is Stop, so capture it through cmd.exe instead.
$JavaVersionOutput = (& cmd.exe /d /s /c 'java -version 2>&1' | Out-String)
if ($LASTEXITCODE -ne 0) {
    Write-Host $JavaVersionOutput
    throw "Java was not found. Install JDK 25, then run this script again."
}

if ($JavaVersionOutput -notmatch 'version "25(?:\.|\")') {
    Write-Host $JavaVersionOutput
    throw "Minecraft 26.3 requires Java 25 for this project. Make sure JDK 25 is first on PATH/JAVA_HOME."
}

New-Item -ItemType Directory -Force -Path $BootstrapRoot | Out-Null

if (-not (Test-Path $GradleBat)) {
    Write-Host "Downloading Gradle $GradleVersion..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $ZipPath

    Write-Host "Extracting Gradle..." -ForegroundColor Yellow
    Expand-Archive -Path $ZipPath -DestinationPath $BootstrapRoot -Force
}

Push-Location $ProjectRoot
try {
    Write-Host "Generating the standard Gradle wrapper..." -ForegroundColor Yellow
    & $GradleBat wrapper --gradle-version $GradleVersion
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle wrapper generation failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host "" 
Write-Host "Bootstrap complete." -ForegroundColor Green
Write-Host "Next command: .\gradlew.bat runClient" -ForegroundColor Green
