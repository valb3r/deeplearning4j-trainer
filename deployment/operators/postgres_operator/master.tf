data "kustomization_overlay" "postgres_operator" {
  namespace = var.postgres_operator_namespace

  resources = [
    "github.com/zalando/postgres-operator/manifests?ref=${var.postgres_operator_version}"
  ]
}

# first loop through resources in ids_prio[0]
resource "kustomization_resource" "p0" {
  for_each = data.kustomization_overlay.postgres_operator.ids_prio[0]

  manifest = data.kustomization_overlay.postgres_operator.manifests[each.value]

  depends_on = [data.kustomization_overlay.postgres_operator]
}

# then loop through resources in ids_prio[1]
# and set an explicit depends_on on kustomization_resource.p0
resource "kustomization_resource" "p1" {
  for_each = data.kustomization_overlay.postgres_operator.ids_prio[1]

  manifest = data.kustomization_overlay.postgres_operator.manifests[each.value]

  depends_on = [kustomization_resource.p0]
}

# finally, loop through resources in ids_prio[2]
# and set an explicit depends_on on kustomization_resource.p1
resource "kustomization_resource" "p2" {
  for_each = data.kustomization_overlay.postgres_operator.ids_prio[2]

  manifest = data.kustomization_overlay.postgres_operator.manifests[each.value]

  depends_on = [kustomization_resource.p1]
}
