# PharmaOps - API & Observability DevOps Lab

PharmaOps est une API backend Spring Boot destinée à la gestion et au suivi d'opérations pharmaceutiques : lots, statuts et audit.

Ce repository sert de lab DevOps complet autour d'une application Java :

- API Spring Boot + PostgreSQL
- Containerisation Docker
- Déploiement Kubernetes
- Observabilité Prometheus, Grafana, Loki, Promtail et Alertmanager
- Dashboards Grafana applicatifs et SRE
- Intégration MCP Grafana pour piloter Grafana depuis un agent IA compatible MCP
- CI GitHub Actions

Les commandes et l'execution des scripts PowerShell sont detaillees dans `instruction.txt`. Consulter ce fichier avant de lancer les scripts du dossier `scripts/`, notamment pour l'ExecutionPolicy PowerShell, le port-forward Grafana, les tokens Grafana, les alertes et le serveur MCP.

Le README est rédigé comme un tutoriel de reprise : il permet de lancer le projet en local, de le déployer sur Kubernetes, puis d'activer toute la stack observability.

---

## Architecture


### Composants

| Composant | Rôle |
|---|---|
| `pharmaops-api` | API Spring Boot Java 21 |
| `pharmaops-db` | Base PostgreSQL |
| Docker | Build et exécution locale |
| Kubernetes | Orchestration applicative |
| Prometheus | Collecte des métriques |
| Grafana | Visualisation metrics, logs et alertes |
| Loki | Stockage et requêtage des logs |
| Promtail | Collecte des logs des pods |
| Alertmanager | Gestion des alertes |
| MCP Grafana | Connexion d'un agent IA compatible MCP vers l'API Grafana |

### Flux observability

```text
pharmaops-api
  -> /actuator/prometheus
  -> ServiceMonitor
  -> Prometheus
  -> Grafana dashboards
```

```text
Pods Kubernetes
  -> stdout / stderr
  -> Promtail
  -> Loki
  -> Grafana dashboards
```

```text
Agent IA compatible MCP
  -> MCP Grafana
  -> Grafana API
  -> Prometheus + Loki + Dashboards
```

---

## Prérequis

Pour reproduire le lab sur un poste local :

- Java 21
- Maven
- Docker Desktop
- Kubernetes activé dans Docker Desktop, ou un cluster Kubernetes accessible
- `kubectl`
- Helm
- Terraform, optionnel
- PowerShell, pour les scripts fournis

Vérifier les outils :

```powershell
java -version
mvn -version
docker version
kubectl version --client
helm version
```

Pour Docker Desktop Kubernetes :

```powershell
kubectl config current-context
```

Contexte attendu :

```text
docker-desktop
```

---

## Structure du repository

```text
.
├── app/pharmaops-api
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
├── infra/
│   ├── k8s/pharmaops-api/
│   │   ├── 00-namespace.yaml
│   │   ├── 10-secret.yaml
│   │   ├── 11-configmap.yaml
│   │   ├── 20-postgres.yaml
│   │   ├── 30-api.yaml
│   │   └── servicemonitor-pharmaops-api.yaml
│   └── terraform/
│       ├── main.tf
│       ├── providers.tf
│       ├── variables.tf
│       └── values/
├── helm/
│   ├── kube-prometheus-stack/
│   └── loki/
├── observability/
│   ├── dashboards/
│   │   ├── panpharma-logs.json
│   │   └── SRE-PANPHARMA.json
│   ├── alerts/
│   └── loki/
├── scripts/
│   ├── create-grafana-mcp-token.ps1
│   ├── apply-grafana-alerts.ps1
│   └── start-grafana-mcp.ps1
├── docs/
│   └── mcp-grafana.md
├── .vscode/
│   └── settings.json
├── docker-compose.yml
├── instruction.txt
└── README.md
```

---

## Mode 1 - Lancer l'application avec Docker Compose

Ce mode lance uniquement l'API et PostgreSQL. Il est utile pour tester l'application rapidement sans Kubernetes.

### 1. Démarrer PostgreSQL et l'API

Depuis la racine du repository :

```powershell
docker compose up --build -d
```

### 2. Vérifier les conteneurs

```powershell
docker compose ps
```

Services attendus :

```text
pharmaops-db
pharmaops-api
```

Ports exposés :

```text
API        : http://localhost:8080
PostgreSQL : localhost:5432
```

### 3. Vérifier l'API

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Endpoint Prometheus exposé par Spring Boot :

```text
http://localhost:8080/actuator/prometheus
```

### 4. Arrêter l'environnement local

```powershell
docker compose down
```

Avec suppression du volume PostgreSQL :

```powershell
docker compose down -v
```

---

## Mode 2 - Déployer l'application sur Kubernetes

Ce mode lance PostgreSQL et l'API dans Kubernetes.

### 1. Construire l'image locale de l'API

Les manifests Kubernetes utilisent l'image :

```text
pharmaops-api:local
```

Construire l'image :

```powershell
docker build -t pharmaops-api:local ./app/pharmaops-api
```

Avec Docker Desktop Kubernetes, cette image locale est directement utilisable si `imagePullPolicy` vaut `IfNotPresent`.

Pour un autre cluster, publier l'image dans un registry puis adapter `infra/k8s/pharmaops-api/30-api.yaml`.

### 2. Appliquer les manifests Kubernetes

```powershell
kubectl apply -f infra/k8s/pharmaops-api/
```

Ressources créées :

```text
Namespace        : pharmaops
Secret           : credentials PostgreSQL
ConfigMap        : configuration DB
Deployment       : pharmaops-db
Service          : pharmaops-db
Deployment       : pharmaops-api
Service          : pharmaops-api
ServiceMonitor   : pharmaops-api
```

### 3. Vérifier les pods et services

```powershell
kubectl get pods -n pharmaops
kubectl get svc -n pharmaops
```

État attendu :

```text
pharmaops-api   1/1 Running
pharmaops-db    1/1 Running
```

### 4. Vérifier la configuration Prometheus de l'API

Le Service `pharmaops-api` expose un port nommé `http`.

Le `ServiceMonitor` utilise ce port pour scraper :

```text
/actuator/prometheus
```

Fichier :

```text
infra/k8s/pharmaops-api/servicemonitor-pharmaops-api.yaml
```

Configuration attendue :

```yaml
selector:
  matchLabels:
    app: pharmaops-api
endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 15s
```

---

## Mode 3 - Installer la stack observability avec Helm

Ce mode installe Prometheus, Grafana, Alertmanager, Loki et Promtail.

Namespaces utilisés :

```text
monitoring
loki
```

### 1. Installer ou mettre à jour Prometheus / Grafana

```powershell
helm upgrade --install monitoring ./helm/kube-prometheus-stack `
  --namespace monitoring `
  --create-namespace `
  -f infra/terraform/values/kube-prometheus-stack.yaml
```

Le fichier de values configure notamment :

- Grafana
- Prometheus
- Alertmanager
- datasource Loki dans Grafana
- découverte des `ServiceMonitor`
- configuration adaptée à un lab Docker Desktop

Fichier :

```text
infra/terraform/values/kube-prometheus-stack.yaml
```

### 2. Installer ou mettre à jour Loki

```powershell
helm upgrade --install loki ./helm/loki `
  --namespace loki `
  --create-namespace `
  -f infra/terraform/values/loki-stack.yaml
```

### 3. Vérifier les pods

```powershell
kubectl get pods -n monitoring
kubectl get pods -n loki
```

Pods attendus côté monitoring :

```text
monitoring-grafana
prometheus-monitoring-kube-prometheus-prometheus-0
alertmanager-monitoring-kube-prometheus-alertmanager-0
monitoring-kube-state-metrics
monitoring-kube-prometheus-operator
```

Pods attendus côté Loki :

```text
loki-0
loki-promtail-...
```

---

## Accéder à Grafana

### 1. Ouvrir un port-forward

```powershell
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
```

URL :

```text
http://localhost:3000
```

Identifiants de lab :

```text
admin / admin
```

### 2. Vérifier Grafana

```powershell
Invoke-RestMethod http://localhost:3000/api/health
```

Résultat attendu :

```json
{
  "database": "ok",
  "version": "12.3.1"
}
```

### 3. Vérifier les datasources

```powershell
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:admin"))
$headers = @{ Authorization = "Basic $basic" }
Invoke-RestMethod -Uri "http://localhost:3000/api/datasources" -Headers $headers
```

Datasources attendues :

| Nom | Type | UID |
|---|---|---|
| Prometheus | `prometheus` | `prometheus` |
| Loki | `loki` | `loki` |
| Alertmanager | `alertmanager` | `alertmanager` |

---

## Vérifier Prometheus

Dans Grafana Explore, choisir la datasource `Prometheus`.

Requête :

```promql
up{namespace="pharmaops"}
```

Résultat attendu :

```text
job="pharmaops-api"   value=1
```

Requêtes utiles :

```promql
http_server_requests_seconds_count{job="pharmaops-api",namespace="pharmaops"}
process_uptime_seconds{job="pharmaops-api",namespace="pharmaops"}
pharmaops_batch_current
kube_pod_status_ready{namespace="pharmaops",condition="true"}
```

---

## Vérifier Loki

Dans Grafana Explore, choisir la datasource `Loki`.

Requête générale :

```logql
{namespace="pharmaops"}
```

Logs de l'API :

```logql
{namespace="pharmaops", pod=~"pharmaops-api-.*"}
```

Logs PostgreSQL :

```logql
{namespace="pharmaops", pod=~"pharmaops-db-.*"}
```

Filtrer les warnings et erreurs :

```logql
{namespace="pharmaops", pod=~"pharmaops-api-.*|pharmaops-db-.*"}
  |~ "(?i)(error|exception|failed|warn)"
```

---

## Importer les dashboards Grafana

Deux dashboards prêts à l'emploi sont fournis.

| Dashboard | Fichier | URL |
|---|---|---|
| Logs applicatifs | `observability/dashboards/panpharma-logs.json` | `/d/panpharma-logs/panpharma-logs` |
| Vue SRE | `observability/dashboards/SRE-PANPHARMA.json` | `/d/sre-panpharma/sre-panpharma` |

### Import via l'API Grafana

Grafana doit être accessible sur :

```text
http://localhost:3000
```

Importer les dashboards :

```powershell
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:admin"))
$headers = @{ Authorization = "Basic $basic" }

foreach ($dashboardPath in @(
  "observability/dashboards/panpharma-logs.json",
  "observability/dashboards/SRE-PANPHARMA.json"
)) {
  $dashboard = Get-Content -Raw -Path $dashboardPath | ConvertFrom-Json
  $payload = @{
    dashboard = $dashboard
    folderUid = ""
    overwrite = $true
    message = "Import $dashboardPath"
  } | ConvertTo-Json -Depth 100

  Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:3000/api/dashboards/db" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $payload
}
```

Ouvrir ensuite :

```text
http://localhost:3000/d/panpharma-logs/panpharma-logs
http://localhost:3000/d/sre-panpharma/sre-panpharma
```

---

## Dashboard `panpharma-logs`

Ce dashboard est dédié aux logs.

Il contient :

- volume de logs par pod
- warnings et erreurs par pod
- logs live PharmaOps
- logs warnings / erreurs / exceptions
- logs PostgreSQL et traces base de données

Variables :

```text
namespace = pharmaops
pod_regex = pharmaops-api-.*|pharmaops-db-.*
```

Réglages par défaut :

```text
Time range : Last 6 hours
Refresh    : 5m
Datasource : Loki
```

---

## Dashboard `SRE-PANPHARMA`

Ce dashboard fournit une vue SRE applicative.

Il contient :

- disponibilité API
- uptime API
- requêtes par minute
- taux d'erreur HTTP 5xx
- latence moyenne
- restarts
- throughput HTTP par endpoint
- latence HTTP par endpoint
- répartition des status codes
- mémoire JVM
- états des threads JVM
- replicas disponibles
- readiness des pods
- restarts par container
- lots par statut métier
- lots créés
- taux de changement de statut des lots
- logs récents warnings / erreurs

Datasources utilisées :

```text
Prometheus
Loki
```

Métriques principales :

```promql
up{job="pharmaops-api",namespace="pharmaops"}
process_uptime_seconds{job="pharmaops-api",namespace="pharmaops"}
http_server_requests_seconds_count{job="pharmaops-api",namespace="pharmaops"}
http_server_requests_seconds_sum{job="pharmaops-api",namespace="pharmaops"}
http_server_requests_seconds_max{job="pharmaops-api",namespace="pharmaops"}
jvm_memory_used_bytes{job="pharmaops-api",namespace="pharmaops"}
jvm_threads_states_threads{job="pharmaops-api",namespace="pharmaops"}
kube_deployment_status_replicas_available{namespace="pharmaops"}
kube_pod_status_ready{namespace="pharmaops"}
kube_pod_container_status_restarts_total{namespace="pharmaops"}
pharmaops_batch_current
pharmaops_batch_created_count_total
pharmaops_batch_status_change_total
```

Note sur les percentiles :

Le dashboard affiche la latence moyenne et la latence max. Pour ajouter des panels p95/p99, l'API doit exposer les buckets histogramme Prometheus :

```text
http_server_requests_seconds_bucket
```

Configuration Spring Boot recommandée pour une évolution future :

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.95, 0.99
```

---

## Configurer les alertes Grafana

Les alertes Grafana du lab sont creees via l'API Grafana avec le script :

```text
scripts/apply-grafana-alerts.ps1
```

Prerequis :

- Grafana accessible sur `http://localhost:3000`
- datasource Prometheus avec l'UID `prometheus`
- datasource Loki avec l'UID `loki`
- token Grafana disponible dans `C:\tmp\panpharma-grafana-mcp-token.txt` ou dans la variable `GRAFANA_TOKEN`

Si le token n'existe pas encore, le creer avec :

```powershell
.\scripts\create-grafana-mcp-token.ps1
```

Appliquer les alertes :

```powershell
.\scripts\apply-grafana-alerts.ps1 -GrafanaUrl "http://localhost:3000"
```

Le script cree le dossier Grafana :

```text
PANPHARMA Alerting
```

Puis le groupe de regles :

```text
panpharma-sre-alerts
```

Alertes creees :

| Alerte | Datasource | Condition |
|---|---|---|
| `PANPHARMA API down` | Prometheus | `up` API inferieur a `1` pendant `1m` |
| `PANPHARMA API target missing` | Prometheus | cible `pharmaops-api` absente de Prometheus pendant `2m` |
| `PANPHARMA API high 5xx rate` | Prometheus | taux HTTP 5xx superieur a `5%` pendant `2m` |
| `PANPHARMA API pod restarted` | Prometheus | au moins un restart du pod API sur `10m` |
| `PANPHARMA API error logs detected` | Loki | logs contenant `error`, `exception`, `failed` ou `refused` sur `10m` |
| `PANPHARMA Prometheus down` | Prometheus | cible Prometheus inferieure a `1` pendant `1m` |

Les alertes portent les labels :

```text
lab=panpharma
team=sre
service=pharmaops-api
severity=critical|warning
```

Verifier dans Grafana :

```text
http://localhost:3000/alerting/list
```

La configuration de notification externe depend de l'environnement cible. Pour un lab local, les alertes sont visibles dans Grafana Alerting. En production ou en cloud, il est possible d'ajouter un contact point email, Slack, Teams, Discord ou webhook, puis de router les alertes avec le label `lab=panpharma`.

---

## Intégration MCP Grafana pour Agent IA

Cette partie est optionnelle. Elle permet à un agent IA compatible MCP de communiquer avec Grafana via le serveur MCP `grafana/mcp-grafana`.

Exemples d'agents ou clients pouvant s'inscrire dans cette approche selon leur support MCP :

- Codex
- GitHub Copilot
- Claude Desktop
- Cursor
- Continue
- Cline
- Roo Code
- Windsurf
- tout autre client compatible MCP

Le principe reste le même :

```text
Agent IA
  -> serveur MCP Grafana
  -> API Grafana
  -> Prometheus, Loki, dashboards et alertes
```

### 1. Configuration VS Code

Fichier :

```text
.vscode/settings.json
```

Configuration :

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

### 2. Créer un token de service Grafana

Le script suivant crée un service account Grafana et enregistre le token hors du repository :

```powershell
.\scripts\create-grafana-mcp-token.ps1
```

Emplacement par défaut :

```text
C:\tmp\panpharma-grafana-mcp-token.txt
```

Le token ne doit pas être commité.

### 3. Lancer le serveur MCP Grafana

```powershell
$env:GRAFANA_SERVICE_ACCOUNT_TOKEN = Get-Content -Raw "C:\tmp\panpharma-grafana-mcp-token.txt"
.\scripts\start-grafana-mcp.ps1 -Detached
```

Endpoint MCP :

```text
http://localhost:8000/sse
```

Healthcheck :

```powershell
Invoke-WebRequest -Uri http://localhost:8000/healthz -UseBasicParsing
```

Arrêter le serveur MCP :

```powershell
docker stop panpharma-mcp-grafana
```

---

## Prompts Grafana et resultats produits

Cette section illustre les interactions utilisees pour construire la partie observabilite Grafana du lab. Chaque exemple montre le prompt formule a l'Agent IA, le traitement effectue via Grafana, Prometheus ou Loki, puis le resultat concret obtenu.

### 1. Verifier les datasources Grafana

Prompt :

```text
Liste-moi les datasources Grafana disponibles.
```

Traitement realise :

```text
Agent IA
  -> MCP Grafana ou API Grafana
  -> lecture des datasources configurees
  -> identification des UID a utiliser dans les dashboards
```

Resultat produit :

| Datasource | Type | UID | Role dans le lab |
|---|---|---|---|
| Prometheus | `prometheus` | `prometheus` | metriques Spring Boot, JVM, HTTP et Kubernetes |
| Loki | `loki` | `loki` | logs applicatifs et logs des pods |
| Alertmanager | `alertmanager` | `alertmanager` | gestion et routage des alertes |

Impact :

```text
Les dashboards et alertes peuvent utiliser des UID stables :
- prometheus
- loki
- alertmanager
```

### 2. Explorer les logs applicatifs avec Loki

Prompt :

```text
Recupere-moi les logs de l'application PharmaOps dans Grafana.
```

Traitement realise :

```text
Agent IA
  -> datasource Loki
  -> recherche des labels disponibles
  -> filtrage sur namespace pharmaops
  -> filtrage sur les pods pharmaops-api
```

Requetes LogQL retenues :

```logql
{namespace="pharmaops"}
```

```logql
{namespace="pharmaops", pod=~"pharmaops-api-.*"}
```

```logql
{namespace="pharmaops", pod=~"pharmaops-api-.*"} |~ "(?i)(warn|error|exception|refused)"
```

Resultat produit :

```text
Les logs applicatifs sont consultables dans Grafana Explore via Loki.
Les filtres namespace et pod permettent d'isoler rapidement pharmaops-api.
```

### 3. Generer le dashboard `panpharma-logs`

Prompt :

```text
Recupere-moi les logs et cree-moi un dashboard appele panpharma-logs.
```

Traitement realise :

```text
Agent IA
  -> datasource Loki
  -> construction de panels LogQL
  -> creation du dashboard dans Grafana
  -> export JSON du dashboard dans le repository
```

Resultat produit :

```text
Dashboard Grafana : panpharma-logs
Fichier           : observability/dashboards/panpharma-logs.json
URL locale        : http://localhost:3000/d/panpharma-logs/panpharma-logs
```

Panels generes :

| Panel | Objectif |
|---|---|
| Log lines by pod | mesurer le volume de logs par pod |
| Warnings and errors by pod | detecter les logs anormaux par pod |
| PharmaOps live logs | suivre les logs applicatifs en direct |
| Warnings, errors and exceptions | isoler les evenements applicatifs importants |
| Database and PostgreSQL logs | analyser les erreurs DB, Hibernate, Hikari ou PostgreSQL |

Variables du dashboard :

```text
namespace = pharmaops
pod_regex = pharmaops-api-.*|pharmaops-db-.*
```

### 4. Corriger les panels Loki trop lourds

Prompt :

```text
Il y a des panels avec le symbole rouge too many outstanding requests, corrige le dashboard.
```

Traitement realise :

```text
Agent IA
  -> analyse des requetes LogQL du dashboard
  -> reduction des requetes trop larges
  -> ajout de filtres namespace et pod
  -> limitation des panels les plus couteux
  -> remplacement des requetes trop generales par des requetes ciblees
```

Resultat produit :

```text
Les panels Loki sont plus stables.
Le dashboard evite les requetes trop volumineuses sur tout le cluster.
Les vues logs restent centrees sur pharmaops-api et pharmaops-db.
```

Principe applique :

```text
Eviter :
{namespace=~".+"}

Preferer :
{namespace="pharmaops", pod=~"pharmaops-api-.*|pharmaops-db-.*"}
```

### 5. Generer le dashboard `SRE-PANPHARMA`

Prompt :

```text
Cree-moi un dashboard applicatif plus SRE appele SRE-PANPHARMA.
```

Traitement realise :

```text
Agent IA
  -> datasource Prometheus
  -> datasource Loki
  -> selection des metriques SRE utiles
  -> creation des panels Grafana
  -> export JSON du dashboard dans le repository
```

Resultat produit :

```text
Dashboard Grafana : SRE-PANPHARMA
Fichier           : observability/dashboards/SRE-PANPHARMA.json
URL locale        : http://localhost:3000/d/sre-panpharma/sre-panpharma
```

PromQL principaux :

```promql
min(up{job="pharmaops-api",namespace="pharmaops"})
```

```promql
sum(rate(http_server_requests_seconds_count{job="pharmaops-api",namespace="pharmaops"}[5m])) * 60
```

```promql
100 * ((sum(rate(http_server_requests_seconds_count{job="pharmaops-api",namespace="pharmaops",status=~"5.."}[5m])) or vector(0)) / clamp_min(sum(rate(http_server_requests_seconds_count{job="pharmaops-api",namespace="pharmaops"}[5m])), 0.001))
```

```promql
sum(increase(kube_pod_container_status_restarts_total{namespace="pharmaops",pod=~"pharmaops-api-.*"}[$__range]))
```

Indicateurs visibles :

- disponibilite API
- uptime API
- requetes par minute
- taux d'erreur HTTP 5xx
- latence moyenne
- restarts pods
- memoire JVM
- etats des threads JVM
- replicas Kubernetes
- readiness pods
- metriques metier PharmaOps
- logs recents warnings / erreurs

### 6. Generer les alertes Grafana

Prompt :

```text
Manager le coté alerte dans Grafana.
```

Traitement realise :

```text
Agent IA
  -> verification des datasources Prometheus et Loki
  -> choix des requetes PromQL et LogQL
  -> creation d'un dossier Grafana Alerting
  -> creation d'un groupe de regles
  -> ajout d'un script PowerShell rejouable
```

Resultat produit :

```text
Dossier Grafana : PANPHARMA Alerting
Groupe          : panpharma-sre-alerts
Script          : scripts/apply-grafana-alerts.ps1
URL locale      : http://localhost:3000/alerting/list
```

Alertes creees :

| Alerte | Datasource | Condition |
|---|---|---|
| `PANPHARMA API down` | Prometheus | `up` API inferieur a `1` |
| `PANPHARMA API target missing` | Prometheus | cible `pharmaops-api` absente |
| `PANPHARMA API high 5xx rate` | Prometheus | taux HTTP 5xx superieur a `5%` |
| `PANPHARMA API pod restarted` | Prometheus | restart recent du pod API |
| `PANPHARMA API error logs detected` | Loki | logs contenant `error`, `exception`, `failed` ou `refused` |
| `PANPHARMA Prometheus down` | Prometheus | cible Prometheus indisponible |

### 7. Verifier l'etat des alertes Grafana

Prompt :

```text
Verifie l'etat des alertes PANPHARMA dans Grafana.
```

Traitement realise :

```text
Agent IA
  -> API Grafana Alerting
  -> lecture du groupe panpharma-sre-alerts
  -> controle des champs state, health et lastEvaluation
```

Resultat attendu :

```text
PANPHARMA API down                  health=ok
PANPHARMA API target missing        health=ok
PANPHARMA API high 5xx rate         health=ok
PANPHARMA API pod restarted         health=ok
PANPHARMA API error logs detected   health=ok
PANPHARMA Prometheus down           health=ok
```

Interprétation :

```text
state=inactive  -> alerte normale, condition non declenchee
state=pending   -> condition detectee mais duree for pas encore atteinte
state=firing    -> alerte active
health=ok       -> regle evaluee correctement par Grafana
```

---

## Option Terraform

Terraform est fourni comme approche alternative pour orchestrer les releases Helm.

Cette option est utile pour :

- standardiser l'installation,
- préparer un déploiement cloud,
- gérer plusieurs environnements,
- industrialiser l'observability stack.

Fichiers :

```text
infra/terraform/main.tf
infra/terraform/providers.tf
infra/terraform/variables.tf
infra/terraform/values/kube-prometheus-stack.yaml
infra/terraform/values/loki-stack.yaml
```

Commandes :

```powershell
cd infra/terraform
terraform init
terraform plan
terraform apply
```

Adapter si besoin :

- le chemin kubeconfig,
- le contexte Kubernetes,
- les versions de charts,
- les namespaces.

---

## CI GitHub Actions

Le repository contient une pipeline CI DevSecOps pour automatiser :

1. checkout du code
2. setup JDK 21
3. build Maven
4. tests
5. SAST avec Semgrep
6. SCA filesystem avec Trivy
7. build image Docker
8. SCA image avec Trivy
9. SCA Maven de l'image API avec Docker Scout
10. push image Docker Hub vers `karl123/pharmaops-api`

Images publiees sur Docker Hub :

```text
karl123/pharmaops-api:latest
karl123/pharmaops-api:<commit-sha>
```

Secret GitHub Actions requis :

```text
DOCKERHUB_TOKEN
```

Le secret `DOCKERHUB_TOKEN` doit contenir un access token Docker Hub autorise a pousser dans le namespace `karl123`.

La CI est volontairement séparée du déploiement. Le déploiement peut ensuite évoluer vers :

- Helm release automatisée,
- Terraform apply contrôlé,
- GitOps avec Argo CD ou Flux.

---

## Runbook complet

Pour reproduire le lab complet :

### 1. Construire l'image API

```powershell
docker build -t pharmaops-api:local ./app/pharmaops-api
```

### 2. Déployer l'application

```powershell
kubectl apply -f infra/k8s/pharmaops-api/
```

### 3. Installer Prometheus / Grafana

```powershell
helm upgrade --install monitoring ./helm/kube-prometheus-stack `
  --namespace monitoring `
  --create-namespace `
  -f infra/terraform/values/kube-prometheus-stack.yaml
```

### 4. Installer Loki

```powershell
helm upgrade --install loki ./helm/loki `
  --namespace loki `
  --create-namespace `
  -f infra/terraform/values/loki-stack.yaml
```

### 5. Vérifier les namespaces

```powershell
kubectl get pods -n pharmaops
kubectl get pods -n monitoring
kubectl get pods -n loki
```

### 6. Exposer Grafana

```powershell
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
```

### 7. Importer les dashboards

```powershell
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:admin"))
$headers = @{ Authorization = "Basic $basic" }

foreach ($dashboardPath in @(
  "observability/dashboards/panpharma-logs.json",
  "observability/dashboards/SRE-PANPHARMA.json"
)) {
  $dashboard = Get-Content -Raw -Path $dashboardPath | ConvertFrom-Json
  $payload = @{
    dashboard = $dashboard
    folderUid = ""
    overwrite = $true
    message = "Import $dashboardPath"
  } | ConvertTo-Json -Depth 100

  Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:3000/api/dashboards/db" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $payload
}
```

### 8. Ouvrir Grafana

```text
http://localhost:3000
```

Dashboards :

```text
http://localhost:3000/d/panpharma-logs/panpharma-logs
http://localhost:3000/d/sre-panpharma/sre-panpharma
```

### 9. Lancer MCP Grafana, optionnel

```powershell
.\scripts\create-grafana-mcp-token.ps1
$env:GRAFANA_SERVICE_ACCOUNT_TOKEN = Get-Content -Raw "C:\tmp\panpharma-grafana-mcp-token.txt"
.\scripts\start-grafana-mcp.ps1 -Detached
```

---

## Commandes utiles

### Application

```powershell
kubectl get pods -n pharmaops
kubectl logs -n pharmaops deploy/pharmaops-api
kubectl logs -n pharmaops deploy/pharmaops-db
```

### Monitoring

```powershell
kubectl get pods -n monitoring
kubectl get svc -n monitoring
```

### Loki

```powershell
kubectl get pods -n loki
```

### Grafana

```powershell
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
```

### MCP Grafana

```powershell
docker ps --filter "name=panpharma-mcp-grafana"
docker logs panpharma-mcp-grafana
docker stop panpharma-mcp-grafana
```

---

## État attendu après installation complète

```text
Namespace pharmaops
  pharmaops-api   Running
  pharmaops-db    Running

Namespace monitoring
  Grafana         Running
  Prometheus      Running
  Alertmanager    Running
  kube-state      Running

Namespace loki
  Loki            Running
  Promtail        Running
```

Grafana :

```text
Prometheus datasource : OK
Loki datasource       : OK
Alertmanager          : OK
panpharma-logs        : disponible
SRE-PANPHARMA         : disponible
```

MCP, optionnel :

```text
http://localhost:8000/sse
```

---

## Auteur

**Karl Yegbe**

Ingénieur DevOps / Full Stack Java
