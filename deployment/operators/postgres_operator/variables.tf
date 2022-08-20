variable "postgres_operator_version" {
  type        = string
  default     = "v1.8.2"
  description = "Version of Postgres Operator per https://github.com/zalando/postgres-operator/releases"
}

variable "postgres_operator_namespace" {
  type        = string
  # TODO: Change to some operator-specific namespace
  default     = "default"
  description = "Namespace for Postgresql operator"
}

