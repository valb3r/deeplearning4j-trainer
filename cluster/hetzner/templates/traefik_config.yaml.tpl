apiVersion: helm.cattle.io/v1
kind: HelmChartConfig
metadata:
  name: traefik
  namespace: kube-system
spec:
  failurePolicy: abort
  valuesContent: |-
    globalArguments: []
    service:
      enabled: true
      type: LoadBalancer
%{ if using_hetzner_lb ~}
      annotations:
        "load-balancer.hetzner.cloud/name": ${name}
        # make hetzners load-balancer connect to our nodes via our private k3s
        "load-balancer.hetzner.cloud/use-private-ip": "true"
        # keep hetzner-ccm from exposing our private ingress ip, which in general isn't routeable from the public internet
        "load-balancer.hetzner.cloud/disable-private-ingress": "true"
        # disable ipv6 by default, because external-dns doesn't support AAAA for hcloud yet https://github.com/kubernetes-sigs/external-dns/issues/2044
        "load-balancer.hetzner.cloud/ipv6-disabled": "${load_balancer_disable_ipv6}"
        "load-balancer.hetzner.cloud/location": "${location}"
        "load-balancer.hetzner.cloud/type": "${load_balancer_type}"
        "load-balancer.hetzner.cloud/uses-proxyprotocol": "true"
%{ endif ~}
    additionalArguments:
      - "--entryPoints.web.proxyProtocol.trustedIPs=127.0.0.1/32,10.0.0.0/8"
      - "--entryPoints.websecure.proxyProtocol.trustedIPs=127.0.0.1/32,10.0.0.0/8"
      - "--entryPoints.web.forwardedHeaders.trustedIPs=127.0.0.1/32,10.0.0.0/8"
      - "--entryPoints.websecure.forwardedHeaders.trustedIPs=127.0.0.1/32,10.0.0.0/8"
%{ for option in traefik_additional_options ~}
      - "${option}"
%{ endfor ~}
%{ if traefik_acme_tls ~}
      - "--certificatesresolvers.le.acme.tlschallenge=true"
      - "--certificatesresolvers.le.acme.email=${traefik_acme_email}"
      - "--certificatesresolvers.le.acme.storage=/data/acme.json"
%{ endif ~}
