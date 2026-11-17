param(
    [string]$Python = ".\.venv\Scripts\python.exe",
    [string]$Salida = "dist",
    [string]$ClavesEvaluacion,
    [string]$RuntimeVC
)

$ErrorActionPreference = "Stop"
if ($ClavesEvaluacion) { $ClavesEvaluacion = (Resolve-Path -LiteralPath $ClavesEvaluacion).Path }
if ($RuntimeVC) { $RuntimeVC = (Resolve-Path -LiteralPath $RuntimeVC).Path }
Push-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
$secretFile = $null
try {
    $project = (Get-Location).Path
    $config = Join-Path $project "config.ini"
    $hook = Join-Path $PSScriptRoot "preparar_vision_empaquetada.py"
    if (-not $RuntimeVC) {
        $vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
        if (Test-Path -LiteralPath $vswhere) {
            $installs = & $vswhere -products "*" -all -property installationPath
            $redist = $installs | ForEach-Object {
                Get-ChildItem -LiteralPath (Join-Path $_ "VC\Redist\MSVC") -Directory -ErrorAction SilentlyContinue
            } | Where-Object Name -Match '^\d+\.\d+\.\d+$' | Sort-Object { [version]$_.Name } -Descending
            $RuntimeVC = $redist | ForEach-Object {
                Get-ChildItem -LiteralPath (Join-Path $_.FullName "x64") -Directory -Filter "Microsoft.VC*.CRT" -ErrorAction SilentlyContinue
            } | Select-Object -ExpandProperty FullName -First 1
        }
    }
    if (-not $RuntimeVC -or -not (Test-Path -LiteralPath (Join-Path $RuntimeVC "msvcp140.dll"))) {
        throw "Indica -RuntimeVC con la carpeta x64 de Visual C++ Redistributable."
    }
    $binaries = @()
    foreach ($dll in Get-ChildItem -LiteralPath $RuntimeVC -Filter "*.dll" -File) {
        $binaries += @("--add-binary", "$($dll.FullName);.")
    }

    $data = @()
    if ($ClavesEvaluacion) {
        New-Item -ItemType Directory -Path "build" -Force | Out-Null
        $secretFile = Join-Path (Get-Item -LiteralPath "build").FullName "integraciones-evaluacion.json"
        & $Python (Join-Path $PSScriptRoot "preparar_claves.py") $ClavesEvaluacion $secretFile
        if ($LASTEXITCODE -ne 0) { throw "No se pudo preparar la configuración de evaluación." }
        $data = @("--add-data", "$secretFile;.")
    }

    # Las tareas de MediaPipe ajenas a la detección de manos arrastran bibliotecas de cálculo.
    $exclude = @("--exclude-module", "jax", "--exclude-module", "jaxlib", "--exclude-module", "scipy")
    & $Python -m PyInstaller --noconfirm --windowed --onefile --name NeuroLink `
        @exclude @binaries @data --collect-data mediapipe --runtime-hook $hook --add-data "$config;." `
        --distpath $Salida --specpath (Join-Path $project "build") main.py
    if ($LASTEXITCODE -ne 0) { throw "No se pudo compilar NeuroLink." }
    $executable = (Resolve-Path -LiteralPath (Join-Path $Salida "NeuroLink.exe")).Path
    $checks = @("--verificar-detector")
    if ($ClavesEvaluacion) { $checks += "--verificar-integraciones" }
    foreach ($check in $checks) {
        $probe = Start-Process -FilePath $executable -ArgumentList $check -WindowStyle Hidden -PassThru
        if (-not $probe.WaitForExit(120000)) {
            $probe.Kill()
            throw "El ejecutable no completó la verificación $check."
        }
        if ($probe.ExitCode -ne 0) { throw "Falló la verificación $check en el ejecutable." }
    }
} finally {
    if ($secretFile -and (Test-Path -LiteralPath $secretFile)) { Remove-Item -LiteralPath $secretFile }
    Pop-Location
}
