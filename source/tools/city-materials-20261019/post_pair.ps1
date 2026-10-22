$ErrorActionPreference='Stop'
$taskPython='C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$taskOut='..\research\city-materials-20261019'
& $taskPython tools/city-materials-20261019/summarize.py --case compare-final2 *> (Join-Path $taskOut 'compare-final2-summary.log')
if($LASTEXITCODE){throw 'Candidate summary failed'}
& $taskPython tools/city-materials-20261019/compare.py *> (Join-Path $taskOut 'comparison.log')
if($LASTEXITCODE){throw 'Matched comparison failed'}
& $taskPython tools/city-materials-20261019/make_comparisons.py *> (Join-Path $taskOut 'comparisons-image.log')
if($LASTEXITCODE){throw 'Native comparison image export failed'}
