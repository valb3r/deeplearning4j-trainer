variable "trainer_app_top_domain" {
  description = "Trainer application domain (top)"
  type        = string
}

variable "github_docker_registry" {
  description = "Github docker registry url"
  type        = string
}

variable "github_docker_registry_user" {
  description = "Github docker registry user"
  type        = string
}

variable "github_docker_registry_password" {
  description = "Github docker registry password"
  type        = string
}

variable "trainer_namespace" {
  type        = string
  default     = "default"
  description = "Namespace for Trainer application"
}

variable "trainer_app_admin_username" {
  type        = string
  description = "Username of Deeplearning4j trainer admin"
}

variable "trainer_app_admin_password" {
  type        = string
  description = "Password of Deeplearning4j trainer admin"
}

variable "trainer_app_s3_access_key_id" {
  type        = string
  description = "S3 Access key id"
}

variable "trainer_app_s3_secret_key" {
  type        = string
  description = "S3 Secret key"
}
