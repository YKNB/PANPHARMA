# PharmaOps – Plateforme API & Observability DevOps

## 📌 Présentation du projet
**PharmaOps** est une API backend Spring Boot destinée à la gestion et au suivi d’opérations pharmaceutiques (lots, statuts, audits).  
Le projet a été conçu comme un **cas d’étude DevOps complet**, intégrant :
- une API Java moderne,
- une observabilité avancée (metrics, logs, alertes),
- une infrastructure Kubernetes,
- une chaîne CI automatisée.

Ce repository documente **l’ensemble du cheminement technique**, depuis le développement jusqu’à l’industrialisation.

---

## 🧱 Architecture globale

### Composants principaux
- **API** : Spring Boot (Java 21, Maven)
- **Base de données** : PostgreSQL
- **Containerisation** : Docker
- **Orchestration** : Kubernetes
- **Observabilité** : Prometheus, Grafana, Alertmanager, Loki, Promtail
- **CI** : GitHub Actions

### Flux logique
1. L’API expose ses endpoints métier et ses métriques via `/actuator/prometheus`
2. Prometheus collecte les métriques via `ServiceMonitor`
3. Promtail collecte les logs des pods
4. Loki indexe les logs
5. Grafana centralise métriques, logs et alertes
6. GitHub Actions automatise build, tests et publication Docker

---

## 📁 Structure du repository

```
.
├── app/pharmaops-api        # Code source Spring Boot
├── infra/
│   ├── k8s/                 # Manifests Kubernetes applicatifs
│   └── terraform/           # Déploiement observabilité via Helm provider
├── helm/                    # Charts Helm (kube-prometheus-stack, loki)
├── observability/
│   ├── dashboards/          # Dashboards Grafana (JSON)
│   ├── alerts/              # Règles d’alerting
│   └── loki/                # Exports et tests de logs
├── .github/workflows        # CI GitHub Actions
└── README.md
```

---

## ⚙️ API Spring Boot

### Fonctionnalités
- Gestion des lots (CRUD)
- Suivi des statuts
- Journalisation des actions (audit)
- Exposition de métriques personnalisées

### Observabilité applicative
- Spring Boot Actuator
- Métriques HTTP
- Métriques métier (`BatchMetrics`)
- Logs structurés

---

## 🐳 Dockerisation

- Image Docker construite à partir du projet Maven
- Image légère et reproductible
- Utilisée aussi bien en local qu’en cluster Kubernetes

Image publiée sur DockerHub :
```
karl123/pharmaops-api
```

Tags :
- `latest`
- `SHA Git` (traçabilité)

---

## ☸️ Déploiement Kubernetes

### Ressources déployées
- Namespace dédié `pharmaops`
- Deployment API
- Service ClusterIP
- PostgreSQL (Deployment + Service)
- ConfigMaps & Secrets
- ServiceMonitor pour Prometheus

Objectif :
> Séparer clairement **logique applicative** et **logique d’observabilité**.

---

                Pod pharmaops-api
                    ↓ (stdout / stderr)
                  Promtail (DaemonSet)
                    ↓
                   Loki
                    ↓
                 Grafana (Logs)


## 📊 Observabilité

### Prometheus
- Déployé via **kube-prometheus-stack**
- Découverte automatique des `ServiceMonitor`
- Collecte des métriques Kubernetes et applicatives

### Grafana
- Datasource Prometheus configurée
- Datasource Loki ajoutée
![log_loki.PNG](https://www.dropbox.com/scl/fi/wgsgr6u5r9ztat7ubdx4k/log_loki.PNG?rlkey=4nd6hxtt01vb5iqugrwj6pzre&dl=0&raw=1)
- Dashboards personnalisés PharmaOps
![dashboard.PNG](https://www.dropbox.com/scl/fi/kqmco3ccsp6iw1hxtjd1r/dashboard.PNG?rlkey=o7hnv88799wk4lwh8dunl0ylv&dl=0&raw=1)
- Dashboards personnalisés PharmaOps avec les logs
![dashboard_obs.PNG](https://www.dropbox.com/scl/fi/sheugn0ayk8915c2klw5w/dashboard_obs.PNG?rlkey=nfg7z4ioyy7f384bonm0ftebz&dl=0&raw=1)
- Alerting Grafana (UI native)
![label-alerte.PNG](https://www.dropbox.com/scl/fi/x0xeexuhip5ul68invky3/label-alerte.PNG?rlkey=jmh77zzgeau82gfycxw6algwh&dl=0&raw=1)
### Loki & Promtail
- Promtail déployé en DaemonSet
- Collecte des logs de tous les pods
- Envoi vers Loki
- Requêtes LogQL fonctionnelles

Logs visibles par :
- namespace
- pod
- container

---

## 🚨 Alerting

- Alertes basées sur métriques Prometheus
- Alertes configurées directement dans Grafana
- Notifications prêtes à être branchées (Slack, email, etc.)

Exemples :
- API down
- Latence élevée
- Erreurs HTTP
![alerte-pharma.PNG](https://www.dropbox.com/scl/fi/916lxfbeg9xxyfr6mm6de/alerte-pharma.PNG?rlkey=a77ow6bp9x7wci8w47dhzz4fc&dl=0&raw=1)
---

## 🔁 CI – GitHub Actions

### Objectifs
Automatiser la validation et la publication sans déployer automatiquement.

### Pipeline CI

Déclencheurs :
- Push sur `main`
- Pull Request

Étapes :
1. Checkout du code
2. Setup JDK 21
3. Build & tests Maven
4. Build image Docker
5. Push image sur DockerHub

### Workflow
- Découplage volontaire du déploiement
- Prêt pour une évolution CD (Helm / GitOps)

---

## 🧠 Choix d’architecture

- Helm utilisé directement pour la stack observabilité
- Terraform utilisé comme **orchestrateur Helm** (Helm provider)
- Séparation claire :
  - CI = build & publication
  - CD = décision humaine / GitOps

Ce choix garantit :
- Lisibilité
- Reproductibilité
- Évolutivité

---

## 🚀 État actuel

✔️ API fonctionnelle
✔️ Observabilité complète
✔️ Logs centralisés
✔️ Alerting opérationnel
✔️ CI industrielle

---

## 👤 Auteur

**Karl Yegbe**  
Ingénieur DevOps / Full Stack Java

