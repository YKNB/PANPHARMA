param(
  [string]$ContainerName = "panpharma-mcp-grafana",
  [string]$GrafanaUrl = "http://host.docker.internal:3000",
  [int]$Port = 8000,
  [switch]$Detached,
  [switch]$ReadOnly
)

$ErrorActionPreference = "Stop"

if (-not $env:GRAFANA_SERVICE_ACCOUNT_TOKEN) {
  throw "Set GRAFANA_SERVICE_ACCOUNT_TOKEN before starting the Grafana MCP server."
}

$dockerArgs = @(
  "run",
  "--rm",
  "--name", $ContainerName,
  "-p", "${Port}:8000",
  "-e", "GRAFANA_URL=$GrafanaUrl",
  "-e", "GRAFANA_SERVICE_ACCOUNT_TOKEN=$env:GRAFANA_SERVICE_ACCOUNT_TOKEN",
  "grafana/mcp-grafana"
)

if ($Detached) {
  $dockerArgs = @("run", "--rm", "-d") + $dockerArgs[2..($dockerArgs.Length - 1)]
}

if ($ReadOnly) {
  $dockerArgs += "--disable-write"
}

docker @dockerArgs
