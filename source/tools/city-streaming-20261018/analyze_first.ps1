$ErrorActionPreference='Stop';$PSNativeCommandUseErrorActionPreference=$false
& 'C:\Users\vakir\miniconda3\python.exe' 'tools\city-streaming-20261018\analyze_native.py' *> 'build\streaming-validation\analyze-first.log'
@{exitCode=$LASTEXITCODE}|ConvertTo-Json|Set-Content -LiteralPath 'build\streaming-validation\analyze-first-receipt.json'