param(
    [ValidateSet('test', 'run', 'experiments')][string]$Task = 'test',
    [string[]]$RunArgs = @()
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force build/classes, libs, traces | Out-Null
    if (-not (Test-Path -LiteralPath libs/junit.jar)) {
        Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar' -OutFile libs/junit.jar
    }
    $sources = @(Get-ChildItem src,test -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 17 -cp libs/junit.jar -d build/classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
    if ($Task -eq 'test') {
        & java -jar libs/junit.jar execute --class-path build/classes --scan-class-path --disable-ansi-colors --details=summary |
            Tee-Object -Variable testOutput
        $testExit = $LASTEXITCODE
        $testText = ($testOutput -join [Environment]::NewLine).TrimEnd()
        Set-Content -Path traces/07-tests.txt -Value $testText -Encoding UTF8
        if ($testExit -ne 0) { throw 'Tests failed' }
    } elseif ($Task -eq 'run') {
        & java -cp build/classes Main @RunArgs
        if ($LASTEXITCODE -ne 0) { throw 'Experiment failed' }
    } else {
        $experiments = @(
            @('01-sharp', 'prompts/react.txt', '5'),
            @('02-vague-1', 'prompts/vague-1.txt', '5'),
            @('03-vague-2', 'prompts/vague-2.txt', '5'),
            @('04-vague-3', 'prompts/vague-3.txt', '5'),
            @('05-step-budget', 'prompts/react.txt', '3', '9223372036854775807', 'hard'),
            @('06-token-budget', 'prompts/react.txt', '3', '600', 'hard')
        )
        foreach ($experiment in $experiments) {
            & java -cp build/classes Main @experiment
            if ($LASTEXITCODE -ne 0) { throw "Experiment $($experiment[0]) failed" }
        }
    }
} finally {
    Pop-Location
}
