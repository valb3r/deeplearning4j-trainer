resource "kubernetes_deployment" "deeplearning4j_trainer" {
  metadata {
    name = "deeplearning4j-trainer"
    namespace = var.trainer_namespace
    labels = {
      app = "deeplearning4j-trainer"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "deeplearning4j-trainer"
      }
    }

    template {
      metadata {
        labels = {
          app = "deeplearning4j-trainer"
        }
      }

      spec {
        image_pull_secrets {
          name = var.docker_pull_secret_ref
        }
        container {
          image = "ghcr.io/valb3r/deeplearning4j_trainer-trainer:latest"
          name  = "deeplearning4j-trainer"
          env {
            name  = "SERVER_PORT"
            value = "8080"
          }
          env {
            name  = "SPRING_DATASOURCE_URL"
            value = "jdbc:postgresql://${var.trainer_db_service_name}:5432/${var.trainer_db_name}?currentSchema=${var.trainer_db_schema_name}"
          }
          env {
            name  = "SPRING_DATASOURCE_HIKARI_SCHEMA"
            value = var.trainer_db_schema_name
          }
          env {
            name  = "SPRING_DATASOURCE_USERNAME"
            value_from {
              secret_key_ref {
                name = "${var.trainer_db_name}-${var.trainer_db_schema_name}-owner-user.${var.trainer_db_service_name}.credentials.postgresql.acid.zalan.do"
                key = "username"
              }
            }
          }
          env {
            name  = "SPRING_DATASOURCE_PASSWORD"
            value_from {
              secret_key_ref {
                name = "${var.trainer_db_name}-${var.trainer_db_schema_name}-owner-user.${var.trainer_db_service_name}.credentials.postgresql.acid.zalan.do"
                key = "password"
              }
            }
          }
          env {
            name  = "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE"
            value = "30"
          }

          resources {
            requests = {
              cpu    = "1"
              memory = "512Mi"
            }
          }

          liveness_probe {
            http_get {
              path = "/actuator/health"
              port = 8080
            }

            initial_delay_seconds = 90
            period_seconds        = 10
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "deeplearning4j_trainer" {
  metadata {
    name = "deeplearning4j-trainer"
    namespace = var.trainer_namespace
  }
  spec {
    selector = {
      app = kubernetes_deployment.deeplearning4j_trainer.metadata[0].labels.app
    }
    session_affinity = "ClientIP"
    port {
      port        = 8080
      target_port = 8080
    }
  }
}

resource "kubernetes_ingress_v1" "deeplearning4j_trainer" {
  metadata {
    name = "deeplearning4j-trainer-ingress"
    namespace = var.trainer_namespace
    annotations = {
      "traefik.ingress.kubernetes.io/router.entrypoints" = "web,websecure"
      "external-dns.alpha.kubernetes.io/hostname" = var.trainer_app_top_domain
    }
  }

  spec {
    rule {
      host = var.trainer_app_top_domain
      http {
        path {
          backend {
            service {
              name = kubernetes_service.deeplearning4j_trainer.metadata[0].name
              port {
                number = kubernetes_service.deeplearning4j_trainer.spec[0].port[0].port
              }
            }
          }

          path = "/api"
          path_type = "Prefix"
        }
      }
    }
  }
}
