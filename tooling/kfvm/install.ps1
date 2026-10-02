& {
    Set-StrictMode -Version Latest
    $ErrorActionPreference = 'Stop'

    function Get-Setting([string] $Name, [string] $Default) {
        $value = [Environment]::GetEnvironmentVariable($Name)
        if ($value) { $value } else { $Default }
    }

    $KofRepo = 'KofLang/Kof4j'
    $UserHome = Get-Setting 'HOME' $env:USERPROFILE
    $KfvmRepo = Get-Setting 'KFVM_REPO' 'KofLang/Kof4j'
    $KfvmRef = Get-Setting 'KFVM_REF' 'lab'
    $KfvmPath = Get-Setting 'KFVM_PATH' 'tooling/kfvm'
    $KfvmHome = Get-Setting 'KFVM_HOME' (Join-Path $UserHome '.local\share\kof')
    $KfvmData = Get-Setting 'KFVM_DATA' (Join-Path $UserHome '.local\share\kfvm')
    $KfvmSource = Get-Setting 'KFVM_SOURCE' ''
    $BinDir = Get-Setting 'KFVM_BIN_DIR' (Join-Path $UserHome '.local\bin')
    $KofMin = Get-Setting 'KOF_MIN' '0.5.0'

    function Write-Info([string] $Message) { Write-Host "kfvm-install: $Message" }

    function Stop-Install([string] $Message) { throw "kfvm-install: error: $Message" }

    function Assert-Command([string] $Name) {
        if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { Stop-Install "'$Name' is required" }
    }

    function Get-Platform {
        if ($env:OS -ne 'Windows_NT') { Stop-Install 'install.ps1 supports Windows only (use install.sh)' }
        switch ($env:PROCESSOR_ARCHITECTURE) {
            'AMD64' { return 'windows-x86_64' }
            'ARM64' { return 'windows-arm64' }
        }
        Stop-Install "unsupported architecture: $env:PROCESSOR_ARCHITECTURE"
    }

    function Save-Download([string] $Url, [string] $Path) {
        $auth = @()
        if ($env:GITHUB_TOKEN -and $Url.StartsWith('https://api.github.com/')) {
            $auth = @('-H', "Authorization: Bearer $env:GITHUB_TOKEN")
        }
        & curl.exe -fsSL --retry 3 @auth -o $Path $Url
        if ($LASTEXITCODE -ne 0) { Stop-Install "download failed: $Url" }
    }

    function ConvertTo-VersionNumber([string] $Text) {
        $core = ($Text -replace '^kof[- ]', '') -replace '[-+ ].*$', ''
        $fields = @($core.Split('.') | ForEach-Object { if ($_ -match '^\d+$') { [int] $_ } else { 0 } }) + @(0, 0, 0)
        [long] ('1{0:D6}{1:D6}{2:D6}' -f $fields[0], $fields[1], $fields[2])
    }

    function Get-KofReleases([string] $Tmp) {
        $file = Join-Path $Tmp 'releases.json'
        Save-Download "https://api.github.com/repos/$KofRepo/releases?per_page=100" $file
        Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json
    }

    function Resolve-KofTag($Releases, [string] $Platform, [long] $Minimum, [bool] $StableOnly) {
        foreach ($release in $Releases) {
            $tag = $release.tag_name
            if (-not $tag.EndsWith("-$Platform")) { continue }
            if ((ConvertTo-VersionNumber $tag) -lt $Minimum) { continue }
            if ($StableOnly -and $release.prerelease) { continue }
            return $tag
        }
    }

    function Assert-Checksum([string] $Dir, [string] $File) {
        $pattern = '^(\S+)\s+\*?' + [regex]::Escape($File) + '$'
        $expected = $null
        foreach ($line in Get-Content -LiteralPath (Join-Path $Dir 'SHA256SUMS')) {
            if ($line.Trim() -match $pattern) {
                $expected = $Matches[1]
                break
            }
        }
        if (-not $expected) { Stop-Install "checksum for $File not found" }
        $actual = (Get-FileHash -LiteralPath (Join-Path $Dir $File) -Algorithm SHA256).Hash
        if ($actual -ne $expected) { Stop-Install "checksum mismatch for $File" }
    }

    function Invoke-Logged([string] $File, [string[]] $Arguments, [string] $Log) {
        $ErrorActionPreference = 'Continue'
        & $File @Arguments *> $Log
        $LASTEXITCODE -eq 0
    }

    function Get-KofVersion([string] $Path) {
        $ErrorActionPreference = 'Continue'
        $output = @(& $Path version 2>$null)
        if ($LASTEXITCODE -eq 0 -and $output.Count -gt 0) { $output[0] }
    }

    function Test-KofVersion([string] $Path, [long] $Minimum) {
        if (-not $Path -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
        $version = Get-KofVersion $Path
        [bool] $version -and (ConvertTo-VersionNumber $version) -ge $Minimum
    }

    function Find-KofBin([long] $Minimum) {
        $candidates = @(
            Get-Command kof -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source
            Join-Path $KfvmHome 'current\bin\kof.bat'
            Get-ChildItem -Path (Join-Path $KfvmHome 'kof-*\bin\kof.bat') -ErrorAction SilentlyContinue | ForEach-Object FullName
        )
        foreach ($candidate in $candidates) {
            if (Test-KofVersion $candidate $Minimum) { return $candidate }
        }
    }

    function Install-Kof([string] $Platform, [long] $Minimum, [string] $Tmp) {
        Write-Info "no Kof >= $KofMin found, installing one"
        $releases = Get-KofReleases $Tmp
        $tag = Resolve-KofTag $releases $Platform $Minimum $true
        if (-not $tag) { $tag = Resolve-KofTag $releases $Platform $Minimum $false }
        if (-not $tag) { Stop-Install "no Kof release >= $KofMin found for $Platform" }
        $encoded = $tag.Replace('+', '%2B')
        $base = "https://github.com/$KofRepo/releases/download/$encoded"
        $archive = "$tag.zip"
        Write-Info "downloading $archive"
        Save-Download "$base/$encoded.zip" (Join-Path $Tmp $archive)
        Save-Download "$base/SHA256SUMS" (Join-Path $Tmp 'SHA256SUMS')
        Assert-Checksum $Tmp $archive
        $target = Join-Path $KfvmHome $tag
        $staging = Join-Path $KfvmHome ('.kfvm-tmp-' + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $staging -Force | Out-Null
        try {
            & tar.exe -xf (Join-Path $Tmp $archive) -C $staging
            if ($LASTEXITCODE -ne 0) { Stop-Install "could not extract $archive" }
            $root = $staging
            $entries = @(Get-ChildItem -LiteralPath $staging -Force)
            if ($entries.Count -eq 1 -and $entries[0].PSIsContainer) { $root = $entries[0].FullName }
            if (-not (Test-Path -LiteralPath (Join-Path $root 'bin\kof.bat'))) { Stop-Install 'archive does not contain bin\kof.bat' }
            if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
            Move-Item -LiteralPath $root -Destination $target
        } finally {
            Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
        }
        $current = Join-Path $KfvmHome 'current'
        if (-not (Test-Path -LiteralPath $current)) { New-Item -ItemType Junction -Path $current -Target $target | Out-Null }
        Join-Path $target 'bin\kof.bat'
    }

    function Test-KfvmSource([string] $Dir) {
        $Dir -and (Test-Path -LiteralPath (Join-Path $Dir 'src') -PathType Container)
    }

    function Get-KfvmSource([string] $Tmp, [string] $Kof) {
        if ($KfvmSource) {
            if (-not (Test-KfvmSource $KfvmSource)) {
                Stop-Install "KFVM_SOURCE has no src directory: $KfvmSource"
            }
            return (Resolve-Path -LiteralPath $KfvmSource).Path
        }
        $bundled = Join-Path (Split-Path -Parent (Split-Path -Parent $Kof)) $KfvmPath
        if (Test-KfvmSource $bundled) {
            Write-Info "using kfvm source from $bundled"
            return $bundled
        }
        Write-Info "downloading kfvm source ($KfvmRepo@${KfvmRef}:$KfvmPath)"
        $repo = Join-Path $Tmp 'repo'
        if (Get-Command git.exe -ErrorAction SilentlyContinue) {
            $cloned = (Invoke-Logged git.exe @('clone', '--quiet', '--depth', '1', '--filter=blob:none', '--sparse', '--branch', $KfvmRef, "https://github.com/$KfvmRepo.git", $repo) (Join-Path $Tmp 'git.log')) -and
                (Invoke-Logged git.exe @('-C', $repo, 'sparse-checkout', 'set', $KfvmPath) (Join-Path $Tmp 'git.log'))
            $src = Join-Path $repo $KfvmPath
            if ($cloned -and (Test-KfvmSource $src)) { return $src }
        }
        $archive = Join-Path $Tmp 'kfvm.tar.gz'
        Save-Download "https://github.com/$KfvmRepo/archive/$KfvmRef.tar.gz" $archive
        $dir = Join-Path $Tmp 'src'
        New-Item -ItemType Directory -Path $dir | Out-Null
        & tar.exe -xzf $archive -C $dir
        if ($LASTEXITCODE -ne 0) { Stop-Install 'could not extract the kfvm source' }
        $root = Get-ChildItem -LiteralPath $dir -Directory | Select-Object -First 1
        $src = if ($root) { Join-Path $root.FullName $KfvmPath }
        if (-not (Test-KfvmSource $src)) { Stop-Install "kfvm source not found in $KfvmRepo@${KfvmRef}:$KfvmPath" }
        $src
    }

    function Find-NativeBin([string] $Dir) {
        foreach ($name in 'kfvm.exe', 'main.exe', '*.exe') {
            $file = Get-ChildItem -LiteralPath $Dir -Recurse -File -Filter $name -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($file) { return $file.FullName }
        }
    }

    function Build-NativeBin([string] $Kof, [string] $Src, [string] $Tmp) {
        $native = Join-Path $Tmp 'native'
        $log = Join-Path $Tmp 'native.log'
        if (-not (Invoke-Logged $Kof @('build', (Join-Path $Src 'src'), '--target', 'native', '--release', '--output', $native) $log)) { return $false }
        if (-not (Test-Path -LiteralPath $native)) { return $false }
        $bin = Find-NativeBin $native
        if (-not $bin -or -not (Invoke-Logged $bin @('-v') $log)) { return $false }
        $launcher = Join-Path $BinDir 'kfvm.exe'
        New-Item -ItemType Directory -Path $BinDir -Force | Out-Null
        Copy-Item -LiteralPath $bin -Destination "$launcher.tmp" -Force
        Move-Item -LiteralPath "$launcher.tmp" -Destination $launcher -Force
        Remove-Item -LiteralPath (Join-Path $KfvmData 'kfvm.jar'), (Join-Path $BinDir 'kfvm.cmd') -Force -ErrorAction SilentlyContinue
        $true
    }

    function Build-Jar([string] $Kof, [string] $Src, [string] $Tmp) {
        $jvm = Join-Path $Tmp 'jvm'
        $log = Join-Path $Tmp 'build.log'
        if (-not (Invoke-Logged $Kof @('build', (Join-Path $Src 'src'), '--release', '--fat', '--output', $jvm) $log)) {
            Get-Content -LiteralPath $log | ForEach-Object { [Console]::Error.WriteLine($_) }
            Stop-Install 'build failed'
        }
        $jar = Join-Path $jvm 'kof-app.jar'
        if (-not (Test-Path -LiteralPath $jar)) { Stop-Install 'build did not produce kof-app.jar' }
        New-Item -ItemType Directory -Path $KfvmData, $BinDir -Force | Out-Null
        Copy-Item -LiteralPath $jar -Destination (Join-Path $KfvmData 'kfvm.jar') -Force
        $launcher = Join-Path $BinDir 'kfvm.cmd'
        Set-Content -LiteralPath "$launcher.tmp" -Value ((New-Launcher) -split '\r?\n') -Encoding Oem
        Move-Item -LiteralPath "$launcher.tmp" -Destination $launcher -Force
        Remove-Item -LiteralPath (Join-Path $BinDir 'kfvm.exe') -Force -ErrorAction SilentlyContinue
    }

    function New-Launcher {
        $jar = Join-Path $KfvmData 'kfvm.jar'
        @"
@echo off
setlocal
if not defined KFVM_HOME if defined HOME (set "KFVM_HOME=%HOME%\.local\share\kof") else (set "KFVM_HOME=%USERPROFILE%\.local\share\kof")
set "JAR=$jar"
set "JAVA="
if exist "%KFVM_HOME%\current\jdk\bin\java.exe" set "JAVA=%KFVM_HOME%\current\jdk\bin\java.exe"
if not defined JAVA for /d %%d in ("%KFVM_HOME%\kof-*") do if not defined JAVA if exist "%%~d\jdk\bin\java.exe" set "JAVA=%%~d\jdk\bin\java.exe"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA where /q java && set "JAVA=java"
if not defined JAVA (
    echo [ERR]: No Java was found ^(install a Kof version or set JAVA_HOME^) 1>&2
    exit /b 1
)
"%JAVA%" -jar "%JAR%" %*
exit /b %ERRORLEVEL%
"@
    }

    function Send-EnvironmentChange {
        [Environment]::SetEnvironmentVariable('KFVM_INSTALL', '1', 'User')
        [Environment]::SetEnvironmentVariable('KFVM_INSTALL', $null, 'User')
    }

    function Add-UserPath {
        if (($env:Path -split ';') -contains $BinDir) { return }
        $env:Path = "$BinDir;$env:Path"
        $environment = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Environment', $true)
        try {
            $current = $environment.GetValue('Path', '', [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
            $entries = @($current -split ';' | Where-Object { $_ })
            if ($entries -contains $BinDir) { return }
            $environment.SetValue('Path', (@($BinDir) + $entries) -join ';', [Microsoft.Win32.RegistryValueKind]::ExpandString)
        } finally {
            $environment.Close()
        }
        Send-EnvironmentChange
        Write-Info "added $BinDir to the user PATH (open a new terminal to use it everywhere)"
    }

    function Install-Kfvm {
        $platform = Get-Platform
        Assert-Command curl.exe
        Assert-Command tar.exe
        $tmp = Join-Path ([IO.Path]::GetTempPath()) ('kfvm-install.' + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $tmp | Out-Null
        try {
            $minimum = ConvertTo-VersionNumber $KofMin
            $kof = Find-KofBin $minimum
            if (-not $kof) { $kof = Install-Kof $platform $minimum $tmp }
            $src = Get-KfvmSource $tmp $kof
            Write-Info "using $kof ($(Get-KofVersion $kof))"
            Write-Info 'building kfvm'
            if (Build-NativeBin $kof $src $tmp) {
                $launcher = Join-Path $BinDir 'kfvm.exe'
                Write-Info "installed native binary to $launcher"
            } else {
                Build-Jar $kof $src $tmp
                $launcher = Join-Path $BinDir 'kfvm.cmd'
                Write-Info "installed $(Join-Path $KfvmData 'kfvm.jar') and launcher $launcher"
            }
            Add-UserPath
            & $launcher -v
        } finally {
            Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    Install-Kfvm
}
