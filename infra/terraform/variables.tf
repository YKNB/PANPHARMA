variable "kubeconfig_path" {
  type    = string
  default = "~/.kube/config"
}

variable "kube_context" {
  type    = string
  default = null
}

variable "monitoring_namespace" {
  type    = string
  default = "monitoring"
}

variable "loki_namespace" {
  type    = string
  default = "loki"
}

# Versions chart à figer (recommandé en prod)
variable "kube_prometheus_stack_chart_version" {
  type    = string
  default = "58.2.0"
}

variable "loki_stack_chart_version" {
  type    = string
  default = "2.10.2"
}
