$repoRoot = git rev-parse --show-toplevel
$srcDir = Join-Path $repoRoot "scripts\hooks"
$dstDir = Join-Path $repoRoot ".git\hooks"

if (-not (Test-Path $dstDir)) {
  Write-Error "Not a git repository or .git/hooks missing"
  exit 1
}

$hooks = @("pre-push", "post-commit")
foreach ($hook in $hooks) {
  $src = Join-Path $srcDir $hook
  $dst = Join-Path $dstDir $hook
  if (-not (Test-Path $src)) {
    Write-Warning "Source hook not found: $src"
    continue
  }
  Copy-Item -Path $src -Destination $dst -Force
  Write-Host "Installed: $hook"
}

Write-Host ""
Write-Host "Hooks installed. Test with: git commit --allow-empty -m 'test hook'"
