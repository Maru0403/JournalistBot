# Kubernetes Deployment Guide — JournalistBot

## Prerequisites

- Kubernetes cluster (local: minikube / kind, cloud: GKE / Railway)
- `kubectl` configured and pointing at the cluster
- Docker image pushed to GHCR by the CD pipeline

---

## Deploy Order

```bash
# 1. Create namespace
kubectl apply -f k8s/namespace.yaml

# 2. Create ConfigMap (non-secret config)
kubectl apply -f k8s/configmap.yaml

# 3. Create Secret (fill in real values first — see below)
kubectl apply -f k8s/secret.yaml

# 4. Deploy databases
kubectl apply -f k8s/mongodb.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml

# 5. Wait for databases to be ready
kubectl wait --for=condition=ready pod -l app=journalist-mongodb -n journalist-bot --timeout=120s
kubectl wait --for=condition=ready pod -l app=journalist-postgres -n journalist-bot --timeout=120s

# 6. Deploy application
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

---

## Configure Secrets

Edit `k8s/secret.yaml` and replace each `<base64-encoded-*>` placeholder:

```bash
# Encode a value:
echo -n "your_actual_value" | base64

# Example:
echo -n "your-discord-bot-token" | base64
# → eW91ci1kaXNjb3JkLWJvdC10b2tlbg==
```

> ⚠️ **Never commit `secret.yaml` with real values!**
> Use [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets) or
> [External Secrets Operator](https://external-secrets.io/) for production.

---

## Useful Commands

```bash
# Check pod status
kubectl get pods -n journalist-bot

# View application logs
kubectl logs -f deployment/journalist-bot -n journalist-bot

# View logs for a specific pod
kubectl logs -f <pod-name> -n journalist-bot

# Check health
kubectl exec -it <pod-name> -n journalist-bot -- wget -qO- http://localhost:8080/actuator/health

# Scale up/down
kubectl scale deployment journalist-bot --replicas=3 -n journalist-bot

# Rolling update (after pushing new image)
kubectl rollout restart deployment/journalist-bot -n journalist-bot

# Check rollout status
kubectl rollout status deployment/journalist-bot -n journalist-bot
```

---

## Architecture

```
                    ┌─────────────────────────────────────┐
                    │         Namespace: journalist-bot    │
                    │                                      │
                    │  ┌──────────────────────┐           │
                    │  │  journalist-bot x2   │           │
                    │  │  (Deployment)        │           │
                    │  └──────────┬───────────┘           │
                    │             │                        │
                    │   ┌─────────┼──────────┐            │
                    │   ▼         ▼          ▼            │
                    │  MongoDB  Postgres   Redis           │
                    │  (SS x1)  (SS x1)   (Dep x1)        │
                    └─────────────────────────────────────┘
```

SS = StatefulSet (persisted data), Dep = Deployment (ephemeral ok)
