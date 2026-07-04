param(
  [string]$GrafanaUrl = "http://localhost:3000",
  [string]$Username = "admin",
  [string]$Password = "admin",
  [string]$ServiceAccountName = "agent-mcp",
  [ValidateSet("Viewer", "Editor", "Admin")]
  [string]$Role = "Editor",
  [string]$TokenPath = "C:\tmp\panpharma-grafana-mcp-token.txt"
)

$ErrorActionPreference = "Stop"

$basicAuth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))
$headers = @{ Authorization = "Basic $basicAuth" }

$search = Invoke-RestMethod `
  -Method Get `
  -Uri "$GrafanaUrl/api/serviceaccounts/search?query=$ServiceAccountName" `
  -Headers $headers `
  -TimeoutSec 10

$serviceAccount = $null
if ($search.serviceAccounts) {
  $serviceAccount = $search.serviceAccounts |
    Where-Object { $_.name -eq $ServiceAccountName } |
    Select-Object -First 1
}

if (-not $serviceAccount) {
  $body = @{
    name = $ServiceAccountName
    role = $Role
    isDisabled = $false
  } | ConvertTo-Json

  $serviceAccount = Invoke-RestMethod `
    -Method Post `
    -Uri "$GrafanaUrl/api/serviceaccounts" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body `
    -TimeoutSec 10
}

$tokenBody = @{
  name = "$ServiceAccountName-$([DateTime]::UtcNow.ToString('yyyyMMddHHmmss'))"
  secondsToLive = 0
} | ConvertTo-Json

$tokenResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "$GrafanaUrl/api/serviceaccounts/$($serviceAccount.id)/tokens" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $tokenBody `
  -TimeoutSec 10

Set-Content -Path $TokenPath -Value $tokenResponse.key -NoNewline -Encoding ASCII

Write-Output "Service account '$ServiceAccountName' ready."
Write-Output "Token saved to $TokenPath"
