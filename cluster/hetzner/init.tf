resource "null_resource" "first_control_plane" {
  connection {
    user           = "root"
    private_key    = var.ssh_private_key
    agent_identity = local.ssh_agent_identity
    host           = module.control_planes[keys(module.control_planes)[0]].ipv4_address
  }

  # Generating k3s master config file
  provisioner "file" {
    content = yamlencode(merge({
      node-name                   = module.control_planes[keys(module.control_planes)[0]].name
      token                       = random_password.k3s_token.result
      cluster-init                = true
      disable-cloud-controller    = true
      disable                     = local.disable_extras
      kubelet-arg                 = ["cloud-provider=external", "volume-plugin-dir=/var/lib/kubelet/volumeplugins"]
      kube-controller-manager-arg = "flex-volume-plugin-dir=/var/lib/kubelet/volumeplugins"
      flannel-iface               = "eth1"
      node-ip                     = module.control_planes[keys(module.control_planes)[0]].private_ipv4_address
      advertise-address           = module.control_planes[keys(module.control_planes)[0]].private_ipv4_address
      node-taint                  = local.control_plane_nodes[keys(module.control_planes)[0]].taints
      node-label                  = local.control_plane_nodes[keys(module.control_planes)[0]].labels
      },
      lookup(local.cni_k3s_settings, var.cni_plugin, {}),
      var.use_control_plane_lb ? {
        tls-san = [hcloud_load_balancer.control_plane.*.ipv4[0], hcloud_load_balancer_network.control_plane.*.ip[0]]
    } : {}))

    destination = "/tmp/config.yaml"
  }

  # Install k3s server
  provisioner "remote-exec" {
    inline = local.install_k3s_server
  }

  # Upon reboot start k3s and wait for it to be ready to receive commands
  provisioner "remote-exec" {
    inline = [
      "systemctl start k3s",
      # prepare the post_install directory
      "mkdir -p /var/post_install",
      # wait for k3s to become ready
      <<-EOT
      timeout 120 bash <<EOF
        until systemctl status k3s > /dev/null; do
          systemctl start k3s
          echo "Waiting for the k3s server to start..."
          sleep 2
        done
        until [ -e /etc/rancher/k3s/k3s.yaml ]; do
          echo "Waiting for kubectl config..."
          sleep 2
        done
        until [[ "\$(kubectl get --raw='/readyz' 2> /dev/null)" == "ok" ]]; do
          echo "Waiting for the cluster to become ready..."
          sleep 2
        done
      EOF
      EOT
    ]
  }

  depends_on = [
    hcloud_network_subnet.control_plane
  ]
}

# Needed for rancher setup
resource "random_password" "rancher_bootstrap" {
  count   = length(var.rancher_bootstrap_password) == 0 ? 1 : 0
  length  = 48
  special = false
}

# This is where all the setup of Kubernetes components happen
resource "null_resource" "kustomization" {
  connection {
    user           = "root"
    private_key    = var.ssh_private_key
    agent_identity = local.ssh_agent_identity
    host           = module.control_planes[keys(module.control_planes)[0]].ipv4_address
  }

  # Upload kustomization.yaml, containing Hetzner CSI & CSM, as well as kured.
  provisioner "file" {
    content = yamlencode({
      apiVersion = "kustomize.config.k8s.io/v1beta1"
      kind       = "Kustomization"

      resources = concat(
        [
          "https://github.com/hetznercloud/hcloud-cloud-controller-manager/releases/download/${local.ccm_version}/ccm-networks.yaml",
          "https://github.com/weaveworks/kured/releases/download/${local.kured_version}/kured-${local.kured_version}-dockerhub.yaml",
          "https://raw.githubusercontent.com/rancher/system-upgrade-controller/master/manifests/system-upgrade-controller.yaml",
        ],
        var.disable_hetzner_csi ? [] : [
          "https://raw.githubusercontent.com/hetznercloud/csi-driver/${local.csi_version}/deploy/kubernetes/hcloud-csi.yml"
        ],
        var.hetzner_dns_enabled ? ["hetzner_dns.yaml"] : [], # External-DNS-Hetzner
        var.traefik_enabled ? ["traefik_config.yaml"] : [],
        lookup(local.cni_install_resources, var.cni_plugin, []),
        var.enable_longhorn ? ["longhorn.yaml"] : [],
        var.enable_cert_manager || var.enable_rancher ? ["cert_manager.yaml"] : [],
        var.enable_rancher ? ["rancher.yaml"] : [],
        var.rancher_registration_manifest_url != "" ? [var.rancher_registration_manifest_url] : []
      ),
      patchesStrategicMerge = concat(
        [
          file("${path.module}/kustomize/kured.yaml"),
          file("${path.module}/kustomize/system-upgrade-controller.yaml"),
          "ccm.yaml",
        ],
        lookup(local.cni_install_resource_patches, var.cni_plugin, [])
      )
    })
    destination = "/var/post_install/kustomization.yaml"
  }

  # Upload hetzner_dns config
  provisioner "file" {
    content = var.hetzner_dns_enabled ? templatefile(
      "${path.module}/templates/hetzner_dns.yaml.tpl",
      {
        hetzner_dns_domain         = var.hetzner_dns_domain
      }) : ""
    destination = "/var/post_install/hetzner_dns.yaml"
  }


  # Upload traefik config
  provisioner "file" {
    content = local.using_klipper_lb || var.traefik_enabled == false ? "" : templatefile(
      "${path.module}/templates/traefik_config.yaml.tpl",
      {
        name                       = "${var.cluster_name}-traefik"
        load_balancer_disable_ipv6 = var.load_balancer_disable_ipv6
        load_balancer_type         = var.load_balancer_type
        location                   = var.load_balancer_location
        traefik_acme_tls           = var.traefik_acme_tls
        traefik_acme_email         = var.traefik_acme_email
        traefik_additional_options = var.traefik_additional_options
        using_hetzner_lb           = !local.using_klipper_lb
        hetzner_dns_enabled        = var.hetzner_dns_enabled
    })
    destination = "/var/post_install/traefik_config.yaml"
  }

  # Upload the CCM patch config
  provisioner "file" {
    content = templatefile(
      "${path.module}/templates/ccm.yaml.tpl",
      {
        cluster_cidr_ipv4                 = local.cluster_cidr_ipv4
        allow_scheduling_on_control_plane = local.allow_scheduling_on_control_plane
        default_lb_location               = var.load_balancer_location
        using_hetzner_lb                  = !local.using_klipper_lb
    })
    destination = "/var/post_install/ccm.yaml"
  }

  # Upload the calico patch config
  provisioner "file" {
    content = templatefile(
      "${path.module}/templates/calico.yaml.tpl",
      {
        cluster_cidr_ipv4 = local.cluster_cidr_ipv4
    })
    destination = "/var/post_install/calico.yaml"
  }

  # Upload the cilium install file
  provisioner "file" {
    content = templatefile(
      "${path.module}/templates/cilium.yaml.tpl",
      {
        values = indent(4, trimspace(fileexists("cilium_values.yaml") ? file("cilium_values.yaml") : local.default_cilium_values))
    })
    destination = "/var/post_install/cilium.yaml"
  }

  # Upload the system upgrade controller plans config
  provisioner "file" {
    content = templatefile(
      "${path.module}/templates/plans.yaml.tpl",
      {
        channel = var.initial_k3s_channel
    })
    destination = "/var/post_install/plans.yaml"
  }

  # Upload the Longhorn config
  provisioner "file" {
    content = templatefile(
      "${path.module}/templates/longhorn.yaml.tpl",
      {
        disable_hetzner_csi = var.disable_hetzner_csi
    })
    destination = "/var/post_install/longhorn.yaml"
  }

  # Upload the cert-manager config
  provisioner "file" {
    content = templatefile(
      "${path.module}/templates/cert_manager.yaml.tpl",
    {})
    destination = "/var/post_install/cert_manager.yaml"
  }

  # Upload the Rancher config
  provisioner "file" {
    content = templatefile(
      "${path.module}/templates/rancher.yaml.tpl",
      {
        rancher_install_channel    = var.rancher_install_channel
        rancher_hostname           = var.rancher_hostname
        rancher_bootstrap_password = length(var.rancher_bootstrap_password) == 0 ? resource.random_password.rancher_bootstrap[0].result : var.rancher_bootstrap_password
        number_control_plane_nodes = length(local.control_plane_nodes)
    })
    destination = "/var/post_install/rancher.yaml"
  }

  # Deploy secrets, logging is automatically disabled due to sensitive variables
  provisioner "remote-exec" {
    inline = [
      "set -ex",
      "kubectl -n kube-system create secret generic hcloud --from-literal=token=${var.hcloud_token} --from-literal=network=${hcloud_network.k3s.name} --dry-run=client -o yaml | kubectl apply -f -",
      "kubectl -n kube-system create secret generic hcloud-csi --from-literal=token=${var.hcloud_token} --dry-run=client -o yaml | kubectl apply -f -",
    ]
  }

  # Install Hetzner DNS secret if needed
  provisioner "remote-exec" {
    inline = var.hetzner_dns_enabled ? [
      "set -ex",
      "kubectl -n kube-system create secret generic hcloud-dns-token --from-literal=token=${var.hcloud_dns_token} --dry-run=client -o yaml | kubectl apply -f -",
    ] : [ "set -ex" ] # NOP
  }

  # Deploy our post-installation kustomization
  provisioner "remote-exec" {
    inline = concat([
      "set -ex",

      # This ugly hack is here, because terraform serializes the
      # embedded yaml files with "- |2", when there is more than
      # one yamldocument in the embedded file. Kustomize does not understand
      # that syntax and tries to parse the blocks content as a file, resulting
      # in weird errors. so gnu sed with funny escaping is used to
      # replace lines like "- |3" by "- |" (yaml block syntax).
      # due to indendation this should not changes the embedded
      # manifests themselves
      "sed -i 's/^- |[0-9]\\+$/- |/g' /var/post_install/kustomization.yaml",

      # Wait for k3s to become ready (we check one more time) because in some edge cases,
      # the cluster had become unvailable for a few seconds, at this very instant.
      <<-EOT
      timeout 180 bash <<EOF
        until [[ "\$(kubectl get --raw='/readyz' 2> /dev/null)" == "ok" ]]; do
          echo "Waiting for the cluster to become ready..."
          sleep 2
        done
      EOF
      EOT
      ,

      # Ready, set, go for the kustomization
      "kubectl apply -k /var/post_install",
      "echo 'Waiting for the system-upgrade-controller deployment to become available...'",
      "kubectl -n system-upgrade wait --for=condition=available --timeout=180s deployment/system-upgrade-controller",
      "sleep 5", # important as the system upgrade controller CRDs sometimes don't get ready right away, especially with Cilium.
      "kubectl -n system-upgrade apply -f /var/post_install/plans.yaml"
      ],
      local.using_klipper_lb || var.traefik_enabled == false ? [] : [
        <<-EOT
      timeout 120 bash <<EOF
      until [ -n "\$(kubectl get -n kube-system service/traefik --output=jsonpath='{.status.loadBalancer.ingress[0].ip}' 2> /dev/null)" ]; do
          echo "Waiting for load-balancer to get an IP..."
          sleep 2
      done
      EOF
      EOT
    ])
  }

  depends_on = [
    null_resource.first_control_plane,
    local_sensitive_file.kubeconfig,
    random_password.rancher_bootstrap
  ]
}
