# Environnement
output "monitoring_namespace" {
  value = var.monitoring_namespace
}

output "loki_namespace" {
  value = var.loki_namespace
}

# Interfaces techniques
output "grafana_service" {
  value = "monitoring-grafana"
}

output "prometheus_service" {
  value = "monitoring-kube-prometheus-prometheus"
}

output "loki_service" {
  value = "loki"
}
