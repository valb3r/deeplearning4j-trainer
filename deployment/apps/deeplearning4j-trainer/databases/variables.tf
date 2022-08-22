variable "deeplearning4j_trainer_namespace" {
  type        = string
  default     = "default"
  description = "Namespace for Deeplearning4j trainer"
}

variable "deeplearning4j_trainer_db_service_name" {
  type        = string
  default     = "deeplearning4jtrainer-deeplearning4j-trainer-db"
  description = "Name for service and resources of deeplearning4j-trainer DB"
}

variable "deeplearning4j_trainer_db_name" {
  type        = string
  default     = "deeplearning4jtrainer"
  description = "Name for service and resources of deeplearning4j-trainer DB"
}

variable "deeplearning4j_trainer_db_team" {
  type        = string
  default     = "deeplearning4jtrainer"
  description = "Team name of deeplearning4j_trainer DB (see postgres-operator for details)"
}

variable "deeplearning4j_trainer_db_schema_name" {
  type        = string
  default     = "deeplearning4jtrainer"
  description = "Schema of deeplearning4j-trainer DB"
}

variable "db_instances" {
  type        = string
  default     = "1"
  description = "Postgresql DB instance count"
}

variable "db_version" {
  type        = string
  default     = "13"
  description = "Postgresql DB version"
}

variable "db_volume_size" {
  type        = string
  default     = "10Gi"
  description = "Postgresql DB volume size"
}


output "deeplearning4j_trainer_db_service_name" {
  value = var.deeplearning4j_trainer_db_service_name
}

output "deeplearning4j_trainer_db_name" {
  value = var.deeplearning4j_trainer_db_name
}

output "deeplearning4j_trainer_db_team_name" {
  value = var.deeplearning4j_trainer_db_team
}

output "deeplearning4j_trainer_db_schema_name" {
  value = var.deeplearning4j_trainer_db_schema_name
}