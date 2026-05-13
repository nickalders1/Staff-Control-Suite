# Staff Control Suite — Setup Guide

## Quick Start (5 minutes)

### What you need

- A running Velocity proxy server
- One or more Paper 1.20.4+ servers behind the proxy
- A Windows 10/11 machine for the desktop app
- Java 17+ on the Velocity/Paper machines
- .NET 8 Runtime on the Windows machine (or use the self-contained build)

---

## Step 1: Install the Proxy Plugin

1. Build or download `staff-control-proxy-1.0.0-SNAPSHOT.jar`
2. Place it in your Velocity `plugins/` directory
3. Start Velocity — the plugin generates `plugins/staffcontrolproxy/config.yml`
4. Stop Velocity
5. Open `plugins/staffcontrolproxy/config.yml`:

```yaml
# Port the Windows desktop app connects to
apiPort: 8080

# Port the Paper agent plugins connect to  
agentPort: 8081

# Auto-generated on first start — do NOT change after setup
apiToken: "abc123..."

# Token shared with ALL agent plugins
agentToken: "xyz789..."

# Optional: restrict which IPs can use the API port
# Leave empty to allow all connections
allowedIps: []

proxyName: "MyNetwork"
databaseType: sqlite
databasePath: staffcontrol.db
sessionExpiryHours: 24
```

> **Important:** The `apiToken` and `agentToken` are auto-generated if they say `CHANGE_ME`.
> After first start, copy the **agentToken** — you'll need it for each Paper server.

6. Restart Velocity
7. Verify: look for `Staff Control Proxy started. API port: 8080, Agent port: 8081` in the console

---

## Step 2: Install Agent Plugins

Repeat for **each** Paper backend server:

1. Build or download `staff-control-agent-1.0.0-SNAPSHOT.jar`
2. Place it in the Paper server's `plugins/` directory
3. Start the Paper server — generates `plugins/StaffControlAgent/config.yml`
4. Stop the server
5. Edit the config:

```yaml
# Unique identifier for this server (lowercase, no spaces, use dashes)
# Examples: lobby, survival, hardcore, events, creative
serverId: "survival"

# Display name shown in the app
serverName: "Survival"

# Server type (lobby/survival/hardcore/events/minigame/generic)
serverType: "survival"

# IP address of your Velocity server (or localhost if on same machine)
proxyHost: "localhost"

# Must match agentPort in Velocity proxy config
proxyAgentPort: 8081

# Must match agentToken in Velocity proxy config (copy-paste!)
agentToken: "xyz789..."

reconnectDelaySeconds: 5
heartbeatIntervalSeconds: 10
```

> **Each server MUST have a unique `serverId`.**

6. Restart the Paper server
7. Verify in Velocity console: `Agent registered: survival (Survival)`

### Common serverId examples

| Server | serverId | serverType |
|---|---|---|
| Lobby/Hub | `lobby` | `lobby` |
| Survival | `survival` | `survival` |
| Hardcore | `hardcore` | `hardcore` |
| Events | `events` | `events` |
| Creative | `creative` | `generic` |
| Minigames | `minigames` | `minigame` |

---

## Step 3: First-Time App Setup

1. Launch `StaffControlSuite.exe`
2. The **First Setup** screen appears (only on first run)
3. Enter your Velocity server connection:
   - Host: IP or hostname of the Velocity machine
   - Port: 8080 (or your configured `apiPort`)
4. Create your **Owner** account:
   - Username: alphanumeric + underscore, 3–32 characters
   - Password: minimum 8 characters
5. Click **Create Owner Account**
6. You're automatically logged in as owner

> The owner account has all permissions and cannot be deleted by other admins.
> Only the owner can create or delete other owner accounts.

---

## Step 4: Register Your Servers

After logging in as owner:

1. Go to **Servers** in the sidebar
2. Click **Add Server**
3. Fill in:
   - **Server ID**: Must exactly match `serverId` in the agent's config.yml
   - **Server Name**: Display name (can be anything)
   - **Server Type**: Informational label
4. Click **Add Server**

The server will show as **Offline** until the Paper agent connects.
Once the agent is running and connects, it will show as **Online** with live stats.

> You must add the server in the app BEFORE the agent can register itself.
> The database entry must exist for registration to succeed.

---

## Step 5: Create Staff Users

1. Go to **Users & Roles** → **Users** tab
2. Click **Add User**
3. Fill in username and password
4. Assign a role (owner, admin, staff, or custom)
5. Click **Create**

Share the login credentials with your staff members.

---

## Step 6: Customize Roles (Optional)

1. Go to **Users & Roles** → **Roles** tab
2. Click **Edit** on a role
3. Check/uncheck permissions as needed
4. Click **Save**

You can also create entirely new roles with **Add Role** (owner only).

---

## Network Topology Examples

### Single Machine Setup
```
[Windows App] ─── localhost:8080 ───┐
                                    │
[Velocity :25577]                   │ (same machine)
├─ [Paper :25565 lobby]  ─── localhost:8081 ─┤
├─ [Paper :25566 survival] ── localhost:8081 ─┤
└─ [Paper :25567 hardcore] ── localhost:8081 ─┘
```

### Remote Setup
```
[Windows App] ─── 1.2.3.4:8080 ───────────────┐
                                               │
[Dedicated Server: 1.2.3.4]                   │
[Velocity :25577]                              │
├─ [Paper :25565 lobby] ─── 1.2.3.4:8081 ─────┤
├─ [Paper :25566 survival] ─ 1.2.3.4:8081 ────┤
└─ [Paper :25567 events] ── 1.2.3.4:8081 ─────┘
```

### Multi-Server with Firewall
```
Firewall rules needed:
  1.2.3.4:8080  ALLOW from staff team IPs only  (app connects here)
  1.2.3.4:8081  ALLOW from Paper server IPs     (agents connect here)
  1.2.3.4:25577 ALLOW from players              (Velocity proxy)
```

---

## Firewall Configuration

### On the Velocity machine (Linux)

```bash
# Allow app connections (restrict to your IP for security)
ufw allow from YOUR_IP to any port 8080

# Allow agent connections from all Paper servers
ufw allow from PAPER_SERVER_IP to any port 8081

# OR allow all (less secure)
ufw allow 8080
ufw allow 8081
```

### On Windows (if running everything locally)

Usually no changes needed for localhost connections.
If Velocity is remote, ensure Windows Firewall allows outbound 8080.

---

## Updating

### Proxy Plugin Update
1. Stop Velocity
2. Replace the JAR in `plugins/`
3. Start Velocity
4. Database migrations run automatically if needed

### Agent Plugin Update
1. Stop the Paper server
2. Replace the JAR in `plugins/`
3. Start the server
4. Config is preserved

### Desktop App Update
1. Close the app
2. Run the new installer or replace the exe
3. Settings (host, port) are preserved in `%AppData%\StaffControlSuite\settings.json`

---

## Troubleshooting

### "Connection refused" in the app
- Is Velocity running?
- Is the apiPort correct?
- Is there a firewall blocking port 8080?
- Try `telnet <host> 8080` from the Windows machine

### Agent shows "Offline" in app
- Check the agent's config — `serverId` must match what you added in the app
- Check that `agentToken` matches exactly (no extra spaces)
- Check Paper server console for error messages
- Check that `proxyHost` and `proxyAgentPort` are correct

### "Invalid credentials" on login
- Passwords are case-sensitive
- If you forgot your owner password: delete the SQLite database file and restart Velocity to reset (this deletes ALL data)
- Database location: `plugins/staffcontrolproxy/staffcontrol.db`

### Console output not appearing
- Verify your user role has `console.view` permission
- Make sure you selected the correct server in the Console dropdown
- Verify the agent is Online in the Servers view

### App crashes on startup
- Ensure .NET 8 Runtime is installed: https://dotnet.microsoft.com/download/dotnet/8.0
- Check Windows Event Viewer for crash details

---

## Config File Reference

### Proxy: `plugins/staffcontrolproxy/config.yml`

| Key | Type | Default | Description |
|---|---|---|---|
| `apiPort` | int | 8080 | Port for Windows app WebSocket connections |
| `agentPort` | int | 8081 | Port for Paper agent WebSocket connections |
| `apiToken` | string | auto | API authentication token (auto-generated) |
| `agentToken` | string | auto | Shared token for all agents (auto-generated) |
| `allowedIps` | list | [] | IP whitelist for API port (empty = allow all) |
| `proxyName` | string | MyProxy | Display name for this proxy instance |
| `databaseType` | string | sqlite | Database type (sqlite only in MVP) |
| `databasePath` | string | staffcontrol.db | Path to SQLite file |
| `sessionExpiryHours` | int | 24 | How long login sessions last |

### Agent: `plugins/StaffControlAgent/config.yml`

| Key | Type | Default | Description |
|---|---|---|---|
| `serverId` | string | survival | Unique server identifier (must be registered in app) |
| `serverName` | string | Survival | Human-readable server name |
| `serverType` | string | survival | Server type label |
| `proxyHost` | string | localhost | Velocity server IP/hostname |
| `proxyAgentPort` | int | 8081 | Must match `agentPort` in proxy config |
| `agentToken` | string | CHANGE_ME | Must match `agentToken` in proxy config |
| `reconnectDelaySeconds` | int | 5 | Delay before reconnect attempt |
| `heartbeatIntervalSeconds` | int | 10 | How often to send heartbeat |
