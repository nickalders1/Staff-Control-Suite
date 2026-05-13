# Staff Control Suite

A professional Minecraft network staff management system consisting of three components that work together to give your staff team a powerful, permission-based control panel.

---

## Architecture

```
Windows Desktop App (WPF)
        │
        │  WebSocket (apiPort, default 8080)
        │  Token: apiToken
        ▼
Velocity Proxy Plugin  ──────────────────────────────────┐
        │                                                │
        │  WebSocket (agentPort, default 8081)           │
        │  Token: agentToken                             │
        ▼                                                │
Paper Agent Plugin (lobby)     ← each backend server     │
Paper Agent Plugin (survival)                            │
Paper Agent Plugin (hardcore)                            │
Paper Agent Plugin (events)                              │
...                                                      │
        └────────────────────────────────────────────────┘
                    (managed by proxy)
```

The Windows app **never** connects directly to backend servers. All communication flows through the Velocity proxy plugin, which acts as the central gateway.

---

## Components

| Component | Tech | Purpose |
|---|---|---|
| `proxy-plugin` | Java 17, Velocity API | Central gateway, API server, database |
| `server-agent` | Java 17, Paper API | Per-server data collection & command execution |
| `desktop-app` | C# .NET 8, WPF | Staff management UI |

---

## Features

### Windows Desktop App
- **Login & First Setup** — Create owner account on first launch
- **Overview Dashboard** — Network-wide status at a glance
- **Network Servers** — View, add, edit, remove servers (permission-based)
- **Console** — Live console output per server, send commands
- **Players** — Network-wide player list with search & filter
- **Player Details** — Full player info, history, playtime
- **Users & Roles** — Full RBAC management
- **Settings** — Connection settings, session info
- **Audit Logs** — Full audit trail of all actions

### Velocity Proxy Plugin
- WebSocket API server for the Windows app
- WebSocket agent server for backend servers
- SQLite database (MariaDB/MySQL planned for v0.2)
- BCrypt password hashing
- Session token authentication
- Per-user permission checking via roles
- Console output streaming
- Network-wide player tracking
- Audit logging
- Command logging with user attribution

### Paper Agent Plugin
- Auto-connects and registers with the proxy
- Streams console output in real time
- Reports TPS, MSPT, player count, world info
- Sends detailed player information (health, gamemode, ping, playtime)
- Executes commands dispatched from the app
- Reconnects automatically if proxy is unreachable

---

## Permissions

| Node | Description |
|---|---|
| `console.view` | View console output |
| `console.command` | Execute console commands |
| `players.view` | View player list |
| `players.details` | View detailed player info |
| `players.punishments.view` | View punishment history |
| `players.punishments.create` | Issue punishments |
| `servers.view` | View server list |
| `servers.manage` | Add/edit/remove servers |
| `users.view` | View user accounts |
| `users.manage` | Create/edit/delete users |
| `roles.view` | View roles |
| `roles.manage` | Create/edit/delete roles & permissions |
| `settings.view` | View settings |
| `settings.manage` | Modify settings |
| `audit.view` | View audit logs |

### Default Roles

| Role | Type | Default Permissions |
|---|---|---|
| `owner` | System | All permissions (cannot be deleted) |
| `admin` | System | All except `roles.manage` |
| `staff` | Default | `console.view`, `players.view`, `players.details`, `servers.view` |

---

## Project Structure

```
staff-control-suite/
├── proxy-plugin/           # Velocity plugin (Maven)
│   ├── pom.xml
│   └── src/main/java/com/staffcontrol/proxy/
│       ├── StaffControlProxy.java
│       ├── api/            # App WebSocket server
│       ├── agent/          # Agent WebSocket server
│       ├── auth/           # Authentication & sessions
│       ├── config/         # Config loader
│       ├── database/       # SQLite database
│       ├── model/          # Data models
│       └── audit/          # Audit logger
├── server-agent/           # Paper plugin (Maven)
│   ├── pom.xml
│   └── src/main/java/com/staffcontrol/agent/
│       ├── StaffControlAgent.java
│       ├── config/         # Config loader
│       ├── proxy/          # WebSocket client
│       ├── console/        # Console capture
│       └── info/           # Server & player info
├── desktop-app/            # WPF application (.NET 8)
│   ├── StaffControlSuite.csproj
│   ├── Models/
│   ├── Services/
│   ├── ViewModels/
│   ├── Views/
│   └── Protocol/
├── shared-protocol/
│   └── protocol.md         # Full protocol specification
└── docs/
    ├── setup-guide.md
    └── database-schema.sql
```

---

## Build Instructions

### Prerequisites

- Java 17+ (for proxy and agent plugins)
- Maven 3.8+ (for proxy and agent builds)
- .NET 8 SDK (for desktop app)
- Velocity 3.x proxy server
- Paper 1.20.4+ for each backend server
- Windows 10/11 (for desktop app)

### Build: Velocity Proxy Plugin

```bash
cd proxy-plugin
mvn clean package -DskipTests
# Output: target/staff-control-proxy-1.0.0-SNAPSHOT.jar
```

Copy the jar to your Velocity `plugins/` folder.

### Build: Paper Agent Plugin

```bash
cd server-agent
mvn clean package -DskipTests
# Output: target/staff-control-agent-1.0.0-SNAPSHOT.jar
```

Copy the jar to each Paper server's `plugins/` folder.

### Build: Desktop App

```powershell
cd desktop-app
dotnet restore
dotnet build --configuration Release
dotnet publish --configuration Release --self-contained false
# Or run directly:
dotnet run
```

---

## Setup Guide

### Step 1: Velocity Proxy Plugin

1. Copy `staff-control-proxy-*.jar` to Velocity's `plugins/` folder.
2. Start Velocity once to generate the config.
3. Stop Velocity.
4. Edit `plugins/staffcontrolproxy/config.yml`:

```yaml
apiPort: 8080           # Port the Windows app connects to
agentPort: 8081         # Port Paper agents connect to
apiToken: "AUTO"        # Auto-generated on first start — copy this!
agentToken: "AUTO"      # Auto-generated on first start — copy this!
allowedIps: []          # Empty = allow all; add IPs to whitelist
proxyName: "MyNetwork"
databaseType: sqlite
databasePath: staffcontrol.db
sessionExpiryHours: 24
```

**The tokens are auto-generated on first start if set to CHANGE_ME.** After first start, copy the generated tokens from the config file.

5. Restart Velocity.

### Step 2: Paper Agent Plugin

1. Copy `staff-control-agent-*.jar` to each Paper server's `plugins/` folder.
2. Start each server once to generate the config.
3. Stop each server.
4. Edit `plugins/StaffControlAgent/config.yml` on each server:

```yaml
serverId: "survival"       # Unique ID for this server (lowercase, no spaces)
serverName: "Survival"     # Display name
serverType: "survival"     # Type: lobby/survival/hardcore/events/generic
proxyHost: "localhost"     # IP of your Velocity server
proxyAgentPort: 8081       # Must match agentPort in proxy config
agentToken: "PASTE_AGENT_TOKEN_HERE"  # From proxy config.yml
reconnectDelaySeconds: 5
heartbeatIntervalSeconds: 10
```

5. Restart each Paper server.

**Each server must have a unique `serverId`.** Example IDs: `lobby`, `survival`, `hardcore`, `events`.

### Step 3: Register Servers in the App

After the proxy plugin starts, servers must be registered in the database before agents can connect:

**Option A (Recommended):** Use the desktop app after first-time setup:
- Open the app → First Setup → create owner account
- Go to Servers → Add Server → fill in serverId, serverName, serverType

**Option B:** The agent will automatically try to register. If the server ID exists in the database, the agent connects. If not, it will log an error.

### Step 4: Desktop App First Setup

1. Start the desktop app.
2. If no owner account exists, the **First Setup** screen appears automatically.
3. Enter your Velocity server's host and `apiPort` (default: localhost:8080).
4. Create your owner account (username + strong password).
5. Login with your owner credentials.

### Step 5: Add Additional Users

As owner:
1. Go to **Users & Roles**
2. Create users, assign roles
3. Customize role permissions as needed

---

## Security Notes

- **Passwords** are never stored in plain text — BCrypt cost 12 is used.
- **API tokens** are long random UUIDs generated on first start.
- **Session tokens** are 64-character random hex strings with configurable expiry.
- **Commands are logged** with user, target server, command text, and timestamp.
- **Audit logs** track all administrative actions.
- **Only the owner** can create/delete other owners or modify system roles.
- For production, place the proxy plugin behind a firewall. The `allowedIps` config can restrict which IPs can connect to the API port.
- HTTPS/WSS support is planned for v0.3.

---

## Roadmap

### v0.2
- [ ] MariaDB/MySQL database backend
- [ ] Server grouping / categories
- [ ] Punishment system (ban, mute, kick, warn) with GUI
- [ ] Player notes system
- [ ] Server restart/stop commands
- [ ] Global broadcast from app
- [ ] Player teleport between servers
- [ ] Console output history buffering (last N lines on subscribe)
- [ ] Multiple proxy support

### v0.3
- [ ] HTTPS / WSS (TLS) support
- [ ] Two-factor authentication (TOTP)
- [ ] Discord webhook integration for audit events
- [ ] Advanced rate limiting per user
- [ ] Dashboard charts (TPS history, player count over time)
- [ ] Scheduled tasks (auto-restart, etc.)
- [ ] Plugin management via app

### v1.0
- [ ] Web-based alternative to desktop app
- [ ] Mobile companion app (view-only)
- [ ] Full punishment appeal system
- [ ] Economy integration
- [ ] Custom plugin reporting API
- [ ] Role-based IP whitelisting
- [ ] SSO / OAuth integration

---

## Troubleshooting

### Agent won't connect to proxy
- Check that `proxyHost` and `proxyAgentPort` in agent config match proxy config
- Check that `agentToken` matches the proxy's `agentToken`
- Ensure the server ID is registered in the proxy database
- Check firewall rules — agent port must be reachable

### Desktop app can't connect
- Check `apiPort` matches proxy config
- Check `apiToken` — not needed for login (the app uses username/password for the initial auth)
- Check Windows Firewall on the proxy machine
- Use `allowedIps` in proxy config to debug IP issues

### Console output not streaming
- Check that `console.view` permission is assigned to your role
- Verify agent is shown as online in the Servers view
- Check proxy logs for WebSocket errors

### Build errors
- Ensure Java 17+ is active: `java -version`
- Ensure Maven 3.8+: `mvn -version`
- Ensure .NET 8 SDK: `dotnet --version`
- Run `mvn dependency:resolve` to check Maven repos are accessible

---

## License

MIT — see LICENSE file.
