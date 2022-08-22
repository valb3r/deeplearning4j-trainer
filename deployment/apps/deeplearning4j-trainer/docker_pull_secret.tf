resource "kubernetes_secret_v1" "docker_pull_secret" {
  metadata {
    name = "github-pull-secret"
    namespace = var.trainer_namespace
  }

  data = {
    ".dockerconfigjson" = jsonencode({
      auths = {
        (var.github_docker_registry) = {
          auth = base64encode("${var.github_docker_registry_user}:${var.github_docker_registry_password}")
        }
      }
    })
  }

  type = "kubernetes.io/dockerconfigjson"
}
