terraform {
  required_providers {
    kubernetes = {
      source = "hashicorp/kubernetes"
      version = "2.7.1"
    }
    kustomization = {
      source = "kbst/kustomization"
      version = "0.7.2"
    }
    local = {
      source  = "hashicorp/local"
      version = ">= 2.0.0, < 3.0.0"
    }
  }
}

provider "kustomization" {
  kubeconfig_path = "${path.module}/../../../cluster/hetzner/_cluster/deeplearning4j-trainer_kubeconfig.yaml"
}
