resource "kubernetes_manifest" "deeplearning4j_trainer_postgres" {
  manifest = {
    "apiVersion" = "acid.zalan.do/v1"
    "kind"       = "postgresql"
    "metadata"   = {
      "name"      = var.deeplearning4j_trainer_db_service_name
      "namespace" = var.deeplearning4j_trainer_namespace
    }
    "spec" = {
      "databases" = {
        "public" = "pgadmin"
      }

      resources = {
        requests = {
          cpu = "0.5"
          memory = "512Mi"
        }
        limits = {
          cpu = "2.0"
          memory = "1024Mi"
        }
      }

      "preparedDatabases" = {
        (var.deeplearning4j_trainer_db_name) = {
          "schemas" = {
            (var.deeplearning4j_trainer_db_schema_name) = {
              "defaultUsers": true
            }
          }
          "extensions" = {
            "pg_trgm" = var.deeplearning4j_trainer_db_schema_name
          }
        }
      }

      "numberOfInstances" = var.db_instances
      "postgresql"        = {
        "version" = var.db_version
        "parameters" = {
          "max_connections" = 200 # 100 is enough for 3 runners, 6 requires 200 ? (max-pool: 5)
        }
      }

      "teamId" = var.deeplearning4j_trainer_db_team

      "users" = {
        "pgadmin"  = ["superuser", "createdb"]
      }

      "volume" = {
        "size" = var.db_volume_size
      }
    }
  }
}
