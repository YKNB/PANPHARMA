resource "kubernetes_namespace" "monitoring" {
  metadata {
    name = var.monitoring_namespace
  }
}

resource "kubernetes_namespace" "loki" {
  metadata {
    name = var.loki_namespace
  }
}

# Repo Helm (Grafana + Prometheus community)
resource "helm_release" "kube_prometheus_stack" {
  name       = "monitoring"
  namespace  = kubernetes_namespace.monitoring.metadata[0].name
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = var.kube_prometheus_stack_chart_version

  create_namespace = false

  values = [
    file("${path.module}/values/kube-prometheus-stack.yaml")
  ]

  # optionnel mais utile en lab
  timeout = 900
}

resource "helm_release" "loki_stack" {
  name       = "loki"
  namespace  = kubernetes_namespace.loki.metadata[0].name
  repository = "https://grafana.github.io/helm-charts"
  chart      = "loki-stack"
  version    = var.loki_stack_chart_version

  create_namespace = false

  values = [
    file("${path.module}/values/loki-stack.yaml")
  ]

  timeout = 900

  depends_on = [
    helm_release.kube_prometheus_stack
  ]
}
