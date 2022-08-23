resource "kubernetes_secret" "trainer_admin_user_credentials" {
  metadata {
    name = "trainer-admin-user-credentials"
  }

  data = {
    username = var.trainer_app_admin_username
    password = var.trainer_app_admin_password
  }

  type = "kubernetes.io/basic-auth"
}

resource "kubernetes_secret" "trainer_admin_s3_credentials" {
  metadata {
    name = "trainer-admin-s3-credentials"
  }

  data = {
    username = var.trainer_app_s3_access_key_id
    password = var.trainer_app_s3_secret_key
  }

  type = "kubernetes.io/basic-auth"
}


resource "kubernetes_deployment" "deeplearning4j_trainer" {
  metadata {
    name = "deeplearning4j-trainer"
    namespace = var.trainer_namespace
    labels = {
      app = "deeplearning4j-trainer"
    }
  }

  spec {
    replicas = 2

    selector {
      match_labels = {
        app = "deeplearning4j-trainer"
      }
    }

    # Downtime strategy
    strategy {
      type = "Recreate"
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
          image_pull_policy = "Always"

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

          env {
            name = "DIRECTORIES_INPUT"
            value = "s3://minio.minio-cluster.svc.cluster.local:443/training-bucket/input/"
          }
          env {
            name = "DIRECTORIES_OUTPUT"
            value = "s3://minio.minio-cluster.svc.cluster.local:443/training-bucket/output/"
          }
          env {
            name = "S3_ACCESS_KEY_ID"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.trainer_admin_s3_credentials.metadata[0].name
                key = "username"
              }
            }
          }
          env {
            name = "S3_SECRET_KEY"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.trainer_admin_s3_credentials.metadata[0].name
                key = "password"
              }
            }
          }
          env {
            name = "S3_IS_HTTP"
            value = "false"
          }

          # Define user:
          env {
            name = "ACCESS_USERS_0_USERNAME"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.trainer_admin_user_credentials.metadata[0].name
                key = "username"
              }
            }
          }
          env {
            name = "ACCESS_USERS_0_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.trainer_admin_user_credentials.metadata[0].name
                key = "password"
              }
            }
          }
          env {
            name = "ACCESS_USERS_0_ROLES"
            value = "USER"
          }

          # Memory config
          env {
            name = "JAVA_TOOL_OPTIONS"
            value = "-Xmx512M -Dorg.bytedeco.javacpp.maxbytes=1G -Dorg.bytedeco.javacpp.maxphysicalbytes=1.5G"
          }

          resources {
            requests = {
              cpu    = "1"
              memory = "1024Mi"
            }

            limits = {
              cpu    = "3"
              memory = "2048Mi"
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

          # Install Kubernetes root certificate for Minio TLS:
          lifecycle {
            post_start {
              exec {
                command = [
                  "/bin/sh", "-c", "cp /var/run/secrets/kubernetes.io/serviceaccount/ca.crt /usr/local/share/ca-certificates/ && update-ca-certificates"
                ]
              }
            }
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

          path = "/"
          path_type = "Prefix"
        }
      }
    }
  }
}
