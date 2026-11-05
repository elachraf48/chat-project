# JavaChat Server — VPS Deployment Guide

## Prerequisites

| Requirement | Details |
|---|---|
| VPS OS | Ubuntu 22.04 LTS (or any modern Debian/RHEL-based Linux) |
| Java | JDK 21 — `sudo apt install openjdk-21-jdk-headless` |
| Open port | TCP **12345** (or your chosen port) in your VPS firewall / cloud security group |
| Local build | `mvn package` run on your dev machine — produces `chat server/target/chat-server.jar` |

---

## Step 1 — Build the fat JAR locally

```bash
# From the project root on your dev machine
mvn package -f "chat server/pom.xml"
# Output: chat server/target/chat-server.jar  (~2 MB)
```

---

## Step 2 — Upload files to the VPS

```bash
# Create the deployment directory
ssh user@YOUR_VPS_IP "sudo mkdir -p /opt/javachat/logs"

# Copy the JAR
scp "chat server/target/chat-server.jar" user@YOUR_VPS_IP:/opt/javachat/

# Copy the logging config (already bundled in the JAR, but easier to edit externally)
scp "chat server/src/main/resources/logging.properties" user@YOUR_VPS_IP:/opt/javachat/

# Copy the systemd unit file
scp "chat server/javachat.service" user@YOUR_VPS_IP:/tmp/javachat.service
```

---

## Step 3 — Create a dedicated service user

```bash
ssh user@YOUR_VPS_IP
sudo adduser --system --no-create-home --group javachat
sudo chown -R javachat:javachat /opt/javachat
```

---

## Step 4 — Configure the Gemini API key (optional)

```bash
# Create the secrets directory
sudo mkdir -p /etc/javachat
sudo tee /etc/javachat/env > /dev/null <<'EOF'
GEMINI_API_KEY=your_gemini_api_key_here
EOF
sudo chmod 600 /etc/javachat/env
sudo chown root:root /etc/javachat/env
```

If you do not have a Gemini API key, skip this step. The server will start normally — AI requests will return an "API key not configured" message to clients.

---

## Step 5 — Install and start the systemd service

```bash
# Install the unit file
sudo mv /tmp/javachat.service /etc/systemd/system/javachat.service
sudo chmod 644 /etc/systemd/system/javachat.service

# Reload systemd and enable auto-start on boot
sudo systemctl daemon-reload
sudo systemctl enable javachat

# Start the server
sudo systemctl start javachat

# Check it is running
sudo systemctl status javachat
```

Expected output:
```
● javachat.service - JavaChat Server
     Loaded: loaded (/etc/systemd/system/javachat.service; enabled)
     Active: active (running) since ...
```

---

## Step 6 — Open the firewall port

```bash
# UFW (Ubuntu default firewall)
sudo ufw allow 12345/tcp
sudo ufw reload
```

Or in your cloud provider's security group / firewall panel, open **TCP inbound on port 12345** from `0.0.0.0/0`.

---

## Step 7 — Verify from your dev machine

```bash
# Quick connectivity test (PowerShell / Windows)
Test-NetConnection -ComputerName YOUR_VPS_IP -Port 12345

# Or with netcat (Linux/macOS)
nc YOUR_VPS_IP 12345
```

You should see the server accept the connection. Send a JSON JOIN message to confirm:
```json
{"type":"JOIN","sender":"testuser"}
```
Expected response:
```json
{"type":"JOIN_SUCCESS","sender":"testuser","timestamp":"..."}
```

---

## Managing the service

| Action | Command |
|---|---|
| Start | `sudo systemctl start javachat` |
| Stop | `sudo systemctl stop javachat` |
| Restart | `sudo systemctl restart javachat` |
| View live logs | `sudo journalctl -u javachat -f` |
| View recent logs | `sudo journalctl -u javachat -n 100` |

---

## Deploying an update

```bash
# 1. Build new JAR locally
mvn package -f "chat server/pom.xml"

# 2. Upload
scp "chat server/target/chat-server.jar" user@YOUR_VPS_IP:/opt/javachat/chat-server.jar

# 3. Restart
ssh user@YOUR_VPS_IP "sudo systemctl restart javachat"
```

---

## Adding TLS (production hardening)

The recommended approach is a **TLS-terminating reverse proxy** in front of the Java server. This avoids managing certificates in Java and allows standard tooling.

### Option A — nginx TCP stream proxy (recommended)

```bash
sudo apt install nginx
```

`/etc/nginx/stream.d/javachat.conf`:
```nginx
upstream javachat_backend {
    server 127.0.0.1:12345;
}

server {
    listen 443;
    proxy_pass javachat_backend;
    ssl_preread on;
}
```

Pair with **Certbot** for a free Let's Encrypt certificate:
```bash
sudo apt install certbot
sudo certbot certonly --standalone -d YOUR_DOMAIN
```

Then reference the cert in the nginx stream config.

### Option B — Java SSLServerSocket (no proxy needed)

Replace `new ServerSocket(port)` in `ChatServer` with an `SSLServerSocket` configured with a PKCS12 keystore. Certbot-issued certs can be converted to PKCS12 with:

```bash
openssl pkcs12 -export \
  -in /etc/letsencrypt/live/YOUR_DOMAIN/fullchain.pem \
  -inkey /etc/letsencrypt/live/YOUR_DOMAIN/privkey.pem \
  -out /opt/javachat/keystore.p12 \
  -name javachat
```

Add to `/etc/javachat/env`:
```
KEYSTORE_PATH=/opt/javachat/keystore.p12
KEYSTORE_PASSWORD=your_password
```

---

## Port reference

| Port | Protocol | Purpose |
|---|---|---|
| 12345 | TCP | JavaChat plain (development / internal) |
| 443 | TCP | JavaChat over TLS via nginx reverse proxy |
| 22 | TCP | SSH (keep restricted to your IP) |

---

## File layout on the VPS

```
/opt/javachat/
├── chat-server.jar       ← deployed fat JAR
├── logging.properties    ← log format / level (editable without rebuild)
└── logs/                 ← optional log file output directory

/etc/javachat/
└── env                   ← secrets (GEMINI_API_KEY, etc.) — mode 0600

/etc/systemd/system/
└── javachat.service      ← systemd unit
```
