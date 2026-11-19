# Builds the fb-client APK in Docker. Nothing is installed on this machine apart from the
# fb-client-build image.
# Usage:  .\build.ps1            build
#         .\build.ps1 -Install   build and install on the connected phone (adb)
#         .\build.ps1 -Debug     build with WebView debugging (inspectable over adb)
#         .\build.ps1 -Clean     also remove the Docker image
param([switch]$Install, [switch]$Debug, [switch]$Clean)
$ErrorActionPreference = 'Stop'
$image = 'fb-client-build'

if ($Clean) {
    docker image rm $image 2>$null
    docker builder prune -f | Out-Null
    Remove-Item -Recurse -Force "$PSScriptRoot\build" -ErrorAction SilentlyContinue
    return
}

if (-not (docker images -q $image)) {
    # No build context (stdin): nothing from this folder is copied into the image
    Get-Content -Raw "$PSScriptRoot\Dockerfile" | docker build -t $image -
    if ($LASTEXITCODE) { throw 'docker build failed' }
}

# The Docker engine runs in WSL (DOCKER_HOST=tcp://127.0.0.1:2375), so E:\x -> /mnt/e/x
$src = $PSScriptRoot -replace '^([A-Za-z]):', { '/mnt/' + $_.Groups[1].Value.ToLower() } -replace '\\', '/'
docker run --rm -e "DEBUG=$(if ($Debug) { 'true' } else { 'false' })" -v "${src}:/src" $image
if ($LASTEXITCODE) { throw 'build failed' }

if ($Install) {
    adb install -r "$PSScriptRoot\out\fb-client.apk"
}
