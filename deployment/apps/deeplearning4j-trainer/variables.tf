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
