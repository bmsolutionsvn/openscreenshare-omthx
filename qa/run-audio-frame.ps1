$ErrorActionPreference = 'Stop'

$gpp = 'C:\msys64\mingw64\bin\g++.exe'
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $PSScriptRoot 'audio-frame.cpp'
$exe = Join-Path $env:TEMP 'omt-audio-frame.exe'

& $gpp -std=c++17 -Wall -Wextra -I (Join-Path $root 'libomt/cpp') $source -o $exe
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $exe
exit $LASTEXITCODE