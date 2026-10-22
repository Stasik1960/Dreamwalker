$ErrorActionPreference='Stop'
$taskPython='C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$taskOut='..\research\city-materials-20261019'
& $taskPython tools/city-materials-20261019/summarize.py --case reopen-final2 *> (Join-Path $taskOut 'reopen-final2-summary.log')
if($LASTEXITCODE){throw 'Reopen summary failed'}
foreach($taskScript in @('verify_visual_evidence','verify_trim_evidence','summarize_repeat_performance')){
    & $taskPython ('tools/city-materials-20261019/'+$taskScript+'.py') *> (Join-Path $taskOut ($taskScript+'.log'))
    if($LASTEXITCODE){throw ($taskScript+' failed; see its log')}
}
