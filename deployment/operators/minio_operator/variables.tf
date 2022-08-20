variable "minio_operator_version" {
  type        = string
  default     = "v4.4.28"
  description = "Version of Minio Operator per https://github.com/minio/operator/releases"
}

variable "minio_operator_namespace" {
  type        = string
  # TODO: Change to some operator-specific namespace
  default     = "default"
  description = "Namespace for Minio operator"
}

