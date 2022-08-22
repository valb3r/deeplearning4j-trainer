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
%{ if hetzner_dns_enabled ~}
      - "--certificatesresolvers.le.acme.dnschallenge.provider=hetzner" # External-DNS-Hetzner
%{ endif ~}
%{ for option in traefik_additional_options ~}
      - "${option}"
%{ endfor ~}
%{ if traefik_acme_tls ~}
%{ if !hetzner_dns_enabled ~}
      - "--certificatesresolvers.le.acme.tlschallenge=true"
%{ endif ~}
      - "--certificatesresolvers.le.acme.email=${traefik_acme_email}"
      - "--certificatesresolvers.le.acme.storage=/data/acme.json"
%{ endif ~}
%{ if hetzner_dns_enabled ~}
    env: # External-DNS-Hetzner
      - name: HETZNER_API_KEY
        valueFrom:
          secretKeyRef:
            name: hcloud-dns-token
            key: token
    ports:
      traefik:
        expose: false
        exposedPort: 9000
        port: 9000
        protocol: TCP
      web:
        expose: true
        exposedPort: 80
        port: 8000
        protocol: TCP
        redirectTo: websecure
      websecure:
        expose: true
        exposedPort: 443
        port: 8443
        protocol: TCP
        tls:
          certResolver: le
          enabled: true
          options: ""
    persistence:
      enabled: true
      name: data
      accessMode: ReadWriteOnce
      size: 128Mi
      storageClass: hcloud-volumes
      path: /data
    deployment:
      # The "volume-permissions" init container is required if you run into permission issues.
      initContainers:
        # Related issue: https://github.com/traefik/traefik/issues/6972
        - name: volume-permissions
          image: busybox:1.31.1
          command: ["sh", "-c", "chmod -Rv 600 /data/*"]
          volumeMounts:
            - name: data
              mountPath: /data
%{ endif ~}
