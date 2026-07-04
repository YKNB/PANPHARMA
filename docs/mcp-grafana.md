# Evolution MCP Grafana

Cette evolution permet a l'agent IA dans VS Code de discuter avec Grafana via le serveur MCP officiel `grafana/mcp-grafana`.

Flux cible :

```text
Agent IA dans VS Code
  -> MCP Grafana
  -> Grafana API
  -> Dashboards + Prometheus + Loki
```

## Prerequis

- Grafana disponible sur `http://localhost:3000`.
- Un token de service account Grafana.
- Docker disponible localement.

Le token ne doit pas etre commite dans le repository. Il est fourni au serveur MCP via la variable d'environnement `GRAFANA_SERVICE_ACCOUNT_TOKEN`.

## 1. Exposer Grafana localement

Depuis le cluster Docker Desktop :

```powershell
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
```

Verification :

```powershell
Invoke-RestMethod http://localhost:3000/api/health
```

## 2. Creer un token Grafana

Option script :

```powershell
.\scripts\create-grafana-mcp-token.ps1
```

Par defaut, le token est enregistre hors repository dans :

```text
C:\tmp\panpharma-grafana-mcp-token.txt
```

Option UI Grafana :

1. Ouvrir `http://localhost:3000`.
2. Se connecter avec `admin` / `admin`.
3. Creer un service account, par exemple `IA_Agent-mcp`.
4. Lui donner le role `Editor` si l'Agent IA doit creer ou modifier des dashboards, ou `Viewer` pour un usage lecture seule.
5. Generer un token et le garder hors du repo.

## 3. Lancer le serveur MCP Grafana

Dans un terminal PowerShell :

```powershell
$env:GRAFANA_SERVICE_ACCOUNT_TOKEN = "<token-grafana>"
.\scripts\start-grafana-mcp.ps1
```

Avec le token genere par le script :

```powershell
$env:GRAFANA_SERVICE_ACCOUNT_TOKEN = Get-Content -Raw "C:\tmp\panpharma-grafana-mcp-token.txt"
.\scripts\start-grafana-mcp.ps1
```

Mode arriere-plan :

```powershell
$env:GRAFANA_SERVICE_ACCOUNT_TOKEN = "<token-grafana>"
.\scripts\start-grafana-mcp.ps1 -Detached
```

Mode lecture seule :

```powershell
$env:GRAFANA_SERVICE_ACCOUNT_TOKEN = "<token-grafana>"
.\scripts\start-grafana-mcp.ps1 -ReadOnly
```

Le serveur MCP expose ensuite son endpoint SSE sur :

```text
http://localhost:8000/sse
```

## 4. Connexion VS Code

La configuration VS Code est dans `.vscode/settings.json` :

```json
{
  "mcp": {
    "servers": {
      "grafana": {
        "type": "sse",
        "url": "http://localhost:8000/sse"
      }
    }
  }
}
```

Une fois le serveur MCP lance, l'agent IA peut interroger Grafana, Prometheus et Loki via l'API Grafana.
