module "databases" {
  source = "./databases"
}

module "services" {
  source = "./services"
  trainer_namespace = var.trainer_namespace
  trainer_app_top_domain = var.trainer_app_top_domain
  docker_pull_secret_ref = kubernetes_secret_v1.docker_pull_secret.metadata[0].name

  trainer_db_service_name = module.databases.deeplearning4j_trainer_db_service_name
  trainer_db_name = module.databases.deeplearning4j_trainer_db_name
  trainer_db_team = module.databases.deeplearning4j_trainer_db_team_name
  trainer_db_schema_name = module.databases.deeplearning4j_trainer_db_name

  trainer_app_admin_username = var.trainer_app_admin_username
  trainer_app_admin_password = var.trainer_app_admin_password
}
