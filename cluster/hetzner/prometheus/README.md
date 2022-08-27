# Install prometheus

See:
https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack

```shell
kubectl create namespace monitoring
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --namespace monitoring --install kube-stack-prometheus prometheus-community/kube-prometheus-stack --set prometheus-node-exporter.hostRootFsMount.enabled=false
```