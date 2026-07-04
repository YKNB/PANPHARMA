param(
  [string]$GrafanaUrl = "http://localhost:3000",
  [string]$TokenPath = "C:\tmp\panpharma-grafana-mcp-token.txt",
  [string]$Token = $env:GRAFANA_TOKEN
)

$ErrorActionPreference = "Stop"

if (-not $Token) {
  if (-not (Test-Path -Path $TokenPath)) {
    throw "Grafana token missing. Set GRAFANA_TOKEN or create $TokenPath."
  }

  $Token = Get-Content -Raw -Path $TokenPath
}

$GrafanaUrl = $GrafanaUrl.TrimEnd("/")
$headers = @{
  Authorization = "Bearer $Token"
  Accept = "application/json"
  "Content-Type" = "application/json"
  "X-Disable-Provenance" = "true"
}

$folderUid = "panpharma-alerting"
$folderTitle = "PANPHARMA Alerting"
$groupName = "panpharma-sre-alerts"

function Ensure-Folder {
  try {
    Invoke-RestMethod -Method Get -Uri "$GrafanaUrl/api/folders/$folderUid" -Headers $headers | Out-Null
    return
  } catch {
    if (-not $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 404) {
      throw
    }
  }

  $body = @{
    uid = $folderUid
    title = $folderTitle
  } | ConvertTo-Json

  Invoke-RestMethod -Method Post -Uri "$GrafanaUrl/api/folders" -Headers $headers -Body $body | Out-Null
}

function New-QueryData {
  param(
    [string]$RefId,
    [string]$DatasourceUid,
    [string]$DatasourceType,
    [string]$Expression,
    [bool]$IsLoki
  )

  $queryType = ""
  if ($IsLoki) {
    $queryType = "instant"
  }

  $model = @{
    datasource = @{
      type = $DatasourceType
      uid = $DatasourceUid
    }
    editorMode = "code"
    expr = $Expression
    intervalMs = 1000
    maxDataPoints = 43200
    refId = $RefId
  }

  if ($IsLoki) {
    $model.queryType = "instant"
  } else {
    $model.instant = $true
    $model.range = $false
  }

  @{
    refId = $RefId
    queryType = $queryType
    datasourceUid = $DatasourceUid
    relativeTimeRange = @{
      from = 600
      to = 0
    }
    model = $model
  }
}

function New-ConditionData {
  param(
    [string]$SourceRef,
    [string]$Operator,
    [double]$Threshold
  )

  @{
    refId = "B"
    queryType = ""
    datasourceUid = "-100"
    relativeTimeRange = @{
      from = 0
      to = 0
    }
    model = @{
      conditions = @(
        @{
          evaluator = @{
            params = @($Threshold)
            type = $Operator
          }
          operator = @{
            type = "and"
          }
          query = @{
            params = @($SourceRef)
          }
          reducer = @{
            params = @()
            type = "last"
          }
          type = "query"
        }
      )
      datasource = @{
        type = "__expr__"
        uid = "-100"
      }
      hide = $false
      intervalMs = 1000
      maxDataPoints = 43200
      refId = "B"
      type = "classic_conditions"
    }
  }
}

function New-Rule {
  param(
    [string]$Uid,
    [string]$Title,
    [string]$DatasourceUid,
    [string]$DatasourceType,
    [string]$Expression,
    [string]$Operator,
    [double]$Threshold,
    [string]$For,
    [string]$Severity,
    [string]$NoDataState,
    [string]$Summary,
    [string]$Description,
    [bool]$IsLoki = $false
  )

  @{
    uid = $Uid
    orgID = 1
    folderUID = $folderUid
    ruleGroup = $groupName
    title = $Title
    condition = "B"
    data = @(
      New-QueryData -RefId "A" -DatasourceUid $DatasourceUid -DatasourceType $DatasourceType -Expression $Expression -IsLoki $IsLoki
      New-ConditionData -SourceRef "A" -Operator $Operator -Threshold $Threshold
    )
    noDataState = $NoDataState
    execErrState = "Error"
    for = $For
    labels = @{
      lab = "panpharma"
      service = "pharmaops-api"
      team = "sre"
      severity = $Severity
    }
    annotations = @{
      summary = $Summary
      description = $Description
      dashboard = "SRE-PANPHARMA"
      logs_dashboard = "panpharma-logs"
    }
    isPaused = $false
  }
}

Ensure-Folder

$rules = @(
  New-Rule `
    -Uid "panpharma-api-down" `
    -Title "PANPHARMA API down" `
    -DatasourceUid "prometheus" `
    -DatasourceType "prometheus" `
    -Expression 'min(up{job="pharmaops-api",namespace="pharmaops"})' `
    -Operator "lt" `
    -Threshold 1 `
    -For "1m" `
    -Severity "critical" `
    -NoDataState "Alerting" `
    -Summary "PharmaOps API is not scraped successfully." `
    -Description "Prometheus reports up=0 for job pharmaops-api in namespace pharmaops."

  New-Rule `
    -Uid "panpharma-api-target-missing" `
    -Title "PANPHARMA API target missing" `
    -DatasourceUid "prometheus" `
    -DatasourceType "prometheus" `
    -Expression 'absent(up{job="pharmaops-api",namespace="pharmaops"})' `
    -Operator "gt" `
    -Threshold 0 `
    -For "2m" `
    -Severity "critical" `
    -NoDataState "OK" `
    -Summary "PharmaOps API target disappeared from Prometheus." `
    -Description "The expected Prometheus target job=pharmaops-api is absent."

  New-Rule `
    -Uid "panpharma-api-5xx-high" `
    -Title "PANPHARMA API high 5xx rate" `
    -DatasourceUid "prometheus" `
    -DatasourceType "prometheus" `
    -Expression '100 * ((sum(rate(http_server_requests_seconds_count{job="pharmaops-api",namespace="pharmaops",status=~"5.."}[5m])) or vector(0)) / clamp_min(sum(rate(http_server_requests_seconds_count{job="pharmaops-api",namespace="pharmaops"}[5m])), 0.001))' `
    -Operator "gt" `
    -Threshold 5 `
    -For "2m" `
    -Severity "warning" `
    -NoDataState "OK" `
    -Summary "PharmaOps API 5xx ratio is above 5 percent." `
    -Description "The percentage of HTTP 5xx responses over the last 5 minutes is above the SRE threshold."

  New-Rule `
    -Uid "panpharma-api-pod-restarts" `
    -Title "PANPHARMA API pod restarted" `
    -DatasourceUid "prometheus" `
    -DatasourceType "prometheus" `
    -Expression 'sum(increase(kube_pod_container_status_restarts_total{namespace="pharmaops",pod=~"pharmaops-api-.*"}[10m]))' `
    -Operator "gt" `
    -Threshold 0 `
    -For "1m" `
    -Severity "warning" `
    -NoDataState "OK" `
    -Summary "A PharmaOps API pod restarted recently." `
    -Description "Kubernetes reports one or more container restarts for pharmaops-api over the last 10 minutes."

  New-Rule `
    -Uid "panpharma-api-log-errors" `
    -Title "PANPHARMA API error logs detected" `
    -DatasourceUid "loki" `
    -DatasourceType "loki" `
    -Expression 'sum(count_over_time({namespace="pharmaops",pod=~"pharmaops-api-.*"} |~ "(?i)(error|exception|failed|refused)" [10m]))' `
    -Operator "gt" `
    -Threshold 0 `
    -For "1m" `
    -Severity "warning" `
    -NoDataState "OK" `
    -Summary "Error-like logs detected for PharmaOps API." `
    -Description "Loki found error, exception, failed, or refused messages in pharmaops-api logs during the last 10 minutes." `
    -IsLoki $true

  New-Rule `
    -Uid "panpharma-prometheus-down" `
    -Title "PANPHARMA Prometheus down" `
    -DatasourceUid "prometheus" `
    -DatasourceType "prometheus" `
    -Expression 'min(up{namespace="monitoring",service="monitoring-kube-prometheus-prometheus",container="prometheus"})' `
    -Operator "lt" `
    -Threshold 1 `
    -For "1m" `
    -Severity "critical" `
    -NoDataState "Alerting" `
    -Summary "Prometheus target is down." `
    -Description "Grafana-managed alerting cannot rely on Prometheus if the Prometheus target is unhealthy."
)

$group = @{
  title = $groupName
  folderUid = $folderUid
  interval = 60
  rules = $rules
}

$payload = $group | ConvertTo-Json -Depth 80
$response = Invoke-RestMethod `
  -Method Put `
  -Uri "$GrafanaUrl/api/v1/provisioning/folder/$folderUid/rule-groups/$groupName" `
  -Headers $headers `
  -Body $payload

Write-Host "Applied Grafana alert group: $($response.title)"
$response.rules | Select-Object uid,title,noDataState,execErrState,for,isPaused
