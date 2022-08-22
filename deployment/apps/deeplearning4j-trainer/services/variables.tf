variable "trainer_app_top_domain" {
  description = "Deployment domain (top)"
  type        = string
}

variable "docker_pull_secret_ref" {
  description = "Docker registry credentials"
  type        = string
}

variable "trainer_namespace" {
  type        = string
  description = "Namespace for Deeplearning4j trainer"
}

variable "trainer_db_service_name" {
  type        = string
  default     = "deeplearning4j-trainer-db"
  description = "Name for service and resources of Deeplearning4j trainer DB"
}

variable "trainer_db_name" {
  type        = string
  default     = "deeplearning4j-trainer"
  description = "Name for service and resources of Deeplearning4j trainer DB"
}

variable "trainer_db_team" {
  type        = string
  default     = "deeplearning4j_trainer"
  description = "Team name of Deeplearning4j trainer DB (see postgres-operator for details)"
}

variable "trainer_db_schema_name" {
  type        = string
  default     = "deeplearning4jtrainer"
  description = "Schema of Deeplearning4j trainer DB"
}
