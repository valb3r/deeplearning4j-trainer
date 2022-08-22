terraform {
  required_providers {
    github = {
      source  = "integrations/github"
      version = ">= 4.0.0, < 5.0.0"
    }
    local = {
      source  = "hashicorp/local"
      version = ">= 2.0.0, < 3.0.0"
    }
    kubernetes = {
      source = "hashicorp/kubernetes"
      version = "2.7.1"
    }
  }
}

provider "kubernetes" {
  config_path = "${path.module}/../../../cluster/hetzner/_cluster/deeplearning4j-trainer_kubeconfig.yaml"
}
