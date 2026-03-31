# Deployment Guide

> Requires: **Docker ≥ 24** and **Docker Compose ≥ 2.20** installed on the Linux server.

---

## 1. Clone the repository

```bash
git clone <your-repo-url>
cd diagram-generator
```

---

## 2. Create the `.env` file on the server

**Never commit `.env` to Git.**
Create it directly on the server by copying the example and filling in real values:

```bash
cp .env.example .env
nano .env          # or: vi .env
```

Minimum required fields:

```dotenv
GEMINI_API_KEY=AIzaSy...          # Your Google Gemini API key
GEMINI_MODEL=gemini-2.5-flash
SERVER_PORT=8080
```

Secure the file so only the current user can read it:

```bash
chmod 600 .env
```

> **How secrets stay safe:**
> `docker-compose.yml` uses `env_file: .env` which injects variables as
> runtime environment variables. The `.dockerignore` file prevents `.env`
> from ever being copied into the Docker image during the build step.

---

## 3. Build and start (background)

```bash
docker compose up -d --build
```

| Flag | Meaning |
|------|---------|
| `-d` | Run in detached (background) mode |
| `--build` | Rebuild the image if source code changed |

First build takes ~2–3 minutes (Maven downloads dependencies).
Subsequent builds use the cached dependency layer and finish in ~30 seconds.

---

## 4. Check the service is running

```bash
docker compose ps
```

Expected output:

```
NAME                  STATUS          PORTS
diagram-generator     Up (healthy)    0.0.0.0:8080->8080/tcp
```

Open in a browser: `http://<your-server-ip>:8080`

---

## 5. View live logs

```bash
docker compose logs -f
```

Filter to errors only:

```bash
docker compose logs -f | grep -i "error\|warn"
```

---

## 6. Stop / restart

```bash
# Stop without removing containers
docker compose stop

# Stop and remove containers (image is kept)
docker compose down

# Restart after a code change
docker compose up -d --build
```

---

## 7. Update to a new version

```bash
git pull
docker compose up -d --build
```

Docker Compose will rebuild the image and restart the container with zero-downtime replacement.

---

## Security notes

| Item | Status |
|------|--------|
| Runs as non-root (`appuser`) inside container | ✅ |
| `.env` excluded from Docker image via `.dockerignore` | ✅ |
| API key injected at runtime only via `env_file` | ✅ |
| JVM heap capped at 75% of container memory limit | ✅ |
| Rate limiting per client IP via `X-Forwarded-For` | ✅ |
| `SERVER_FORWARD_HEADERS_STRATEGY=NATIVE` (trusts proxy headers) | ⚠️ Only safe behind a trusted reverse proxy (Nginx, Traefik). Remove this line if the container is directly exposed to the internet. |

---

## Optional: Nginx reverse proxy

If you place Nginx in front, add to your Nginx site config:

```nginx
location / {
    proxy_pass         http://127.0.0.1:8080;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
}
```

This ensures `RateLimitFilter` receives the real client IP.
