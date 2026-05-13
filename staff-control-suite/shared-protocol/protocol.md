# Staff Control Suite — WebSocket Protocol Specification

## Overview

The system uses two separate WebSocket servers on the Velocity proxy:

| Server | Default Port | Config Key | Used By |
|---|---|---|---|
| API Server | 8080 | `apiPort` | Windows Desktop App |
| Agent Server | 8081 | `agentPort` | Paper Agent Plugins |

All messages are JSON-encoded UTF-8 text frames.

---

## App ↔ Proxy Protocol (API Server)

### Request Envelope

Sent by the Windows app to the proxy:

```json
{
  "type": "message_type",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "token": "session_token_or_null",
  "payload": {}
}
```

- `type` — identifies the operation
- `requestId` — UUID, used to match async responses
- `token` — session token (omit or null for `setup.*` and `auth.login`)
- `payload` — operation-specific data

### Response Envelope

Sent by proxy back to app, always includes `requestId` matching the request:

**Success:**
```json
{
  "type": "response",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "payload": {}
}
```

**Error:**
```json
{
  "type": "response",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "success": false,
  "error": "ERROR_CODE",
  "message": "Human-readable error description"
}
```

### Server-Pushed Events

The proxy sends these without a corresponding request:

```json
{
  "type": "event.XXX",
  "payload": {}
}
```

---

## Message Type Reference

### Setup & Authentication

#### `setup.check`
Check if owner account exists (no auth required).
```json
// Request
{"type":"setup.check","requestId":"...","payload":{}}

// Response
{"type":"response","requestId":"...","success":true,"payload":{"needsSetup":true}}
```

#### `setup.create`
Create the initial owner account. Only works if no owner exists.
```json
// Request
{"type":"setup.create","requestId":"...","payload":{"username":"admin","password":"securepass123"}}

// Response
{"type":"response","requestId":"...","success":true,"payload":{
  "token":"abc123...",
  "user":{"id":1,"username":"admin","roleName":"owner","permissions":["console.view",...],"isActive":true}
}}
```

Errors: `SETUP_ALREADY_DONE`, `VALIDATION_ERROR`, `INVALID_USERNAME`, `WEAK_PASSWORD`

#### `auth.login`
Authenticate with username and password.
```json
// Request
{"type":"auth.login","requestId":"...","payload":{"username":"staff1","password":"pass"}}

// Response
{"type":"response","requestId":"...","success":true,"payload":{
  "token":"abc123...",
  "user":{"id":2,"username":"staff1","roleName":"staff","permissions":["console.view","players.view"],"isActive":true}
}}
```

Errors: `INVALID_CREDENTIALS`, `ACCOUNT_DISABLED`

#### `auth.logout`
Invalidate current session. Requires valid token.
```json
// Request
{"type":"auth.logout","requestId":"...","token":"abc123...","payload":{}}

// Response
{"type":"response","requestId":"...","success":true,"payload":{}}
```

---

### Servers

#### `servers.list`
Requires: `servers.view`
```json
// Request
{"type":"servers.list","requestId":"...","token":"...","payload":{}}

// Response payload
{"servers":[
  {
    "serverId":"survival",
    "serverName":"Survival",
    "serverType":"survival",
    "host":"localhost",
    "port":25566,
    "isOnline":true,
    "playerCount":12,
    "maxPlayers":100,
    "tps":19.98,
    "mspt":2.5,
    "lastHeartbeat":1700000000000,
    "minecraftVersion":"git-Paper-123 (MC: 1.20.4)",
    "paperVersion":"1.20.4-R0.1-SNAPSHOT",
    "pluginCount":15,
    "loadedChunks":423
  }
]}
```

#### `servers.add`
Requires: `servers.manage`
```json
// Request payload
{"serverId":"events","serverName":"Events","serverType":"events"}

// Response payload
{"server":{...ServerInfo...}}
```

The proxy generates an `agentToken` automatically.

Errors: `SERVER_ALREADY_EXISTS`, `VALIDATION_ERROR`

#### `servers.remove`
Requires: `servers.manage`
```json
// Request payload
{"serverId":"events"}
```

#### `servers.update`
Requires: `servers.manage`
```json
// Request payload
{"serverId":"events","serverName":"Events Server","serverType":"events"}
```

---

### Console

#### `console.subscribe`
Requires: `console.view`. Subscribe to live console output for a server.
```json
// Request payload
{"serverId":"survival"}
```

#### `console.unsubscribe`
Unsubscribe from console output.
```json
// Request payload
{"serverId":"survival"}
```

#### `console.command`
Requires: `console.command`. Send a command to a server.
```json
// Request payload
{"serverId":"survival","command":"say Hello World"}
```

Errors: `SERVER_OFFLINE`, `EMPTY_COMMAND`

---

### Players

#### `players.list`
Requires: `players.view`
```json
// Response payload
{"players":[
  {
    "uuid":"550e8400-e29b-41d4-a716-446655440000",
    "name":"Notch",
    "serverId":"survival",
    "world":"world",
    "gamemode":"SURVIVAL",
    "health":18.5,
    "foodLevel":17,
    "ping":42,
    "isOnline":true,
    "firstJoined":1690000000000,
    "lastJoined":1700000000000,
    "playtimeSeconds":36000
  }
]}
```

#### `players.details`
Requires: `players.details`
```json
// Request payload
{"uuid":"550e8400-e29b-41d4-a716-446655440000"}

// Response payload
{"player":{...PlayerInfo...}}
```

---

### Users

#### `users.list`
Requires: `users.view`
```json
// Response payload
{"users":[
  {
    "id":1,
    "username":"admin",
    "roleId":1,
    "roleName":"owner",
    "permissions":["console.view",...],
    "isActive":true,
    "createdAt":1690000000000
  }
]}
```

#### `users.create`
Requires: `users.manage`
```json
// Request payload
{"username":"newstaff","password":"pass1234","roleId":3}
```

Errors: `USER_ALREADY_EXISTS`, `VALIDATION_ERROR`, `ROLE_NOT_FOUND`, `INSUFFICIENT_PRIVILEGES`

#### `users.update`
Requires: `users.manage`
```json
// Request payload
{"userId":3,"username":"newstaff","roleId":3,"isActive":true}
```

Errors: `USER_NOT_FOUND`, `INSUFFICIENT_PRIVILEGES`

#### `users.delete`
Requires: `users.manage`
```json
// Request payload
{"userId":3}
```

Errors: `USER_NOT_FOUND`, `CANNOT_DELETE_SELF`, `INSUFFICIENT_PRIVILEGES`

---

### Roles

#### `roles.list`
Requires: `roles.view`
```json
// Response payload
{"roles":[
  {
    "id":1,
    "name":"owner",
    "displayName":"Owner",
    "permissions":["console.view",...],
    "isSystemRole":true,
    "createdAt":1690000000000
  }
]}
```

#### `roles.create`
Requires: `roles.manage` + owner
```json
// Request payload
{"name":"moderator","displayName":"Moderator","permissions":["console.view","players.view","players.details","players.punishments.create"]}
```

#### `roles.update`
Requires: `roles.manage`
```json
// Request payload
{"roleId":4,"displayName":"Senior Moderator","permissions":["console.view","console.command","players.view","players.details"]}
```

Non-owners cannot modify system roles' permissions.

#### `roles.delete`
Requires: `roles.manage` + owner
```json
// Request payload
{"roleId":4}
```

System roles cannot be deleted.

---

### Audit Logs

#### `audit.list`
Requires: `audit.view`
```json
// Request payload
{"page":1,"limit":50}

// Response payload
{"logs":[
  {
    "id":1,
    "userId":1,
    "username":"admin",
    "action":"LOGIN",
    "target":null,
    "details":"Logged in from 127.0.0.1",
    "ipAddress":"127.0.0.1",
    "timestamp":1700000000000
  }
],"total":1234}
```

---

### Events (Server → App)

#### `event.console.line`
Sent to all sessions subscribed to the server with `console.view` permission:
```json
{
  "type":"event.console.line",
  "payload":{
    "serverId":"survival",
    "line":"[00:00:00 INFO]: Player Notch joined the game",
    "timestamp":1700000000000
  }
}
```

#### `event.server.status`
Sent to all sessions with `servers.view` when a server connects/disconnects or heartbeat:
```json
{
  "type":"event.server.status",
  "payload":{
    "serverId":"survival",
    "online":true,
    "playerCount":12,
    "tps":19.98,
    "mspt":2.5,
    "lastHeartbeat":1700000000000
  }
}
```

#### `event.player.update`
Sent to sessions with `players.view` permission:
```json
{
  "type":"event.player.update",
  "payload":{
    "serverId":"survival",
    "players":[{...PlayerInfo...}]
  }
}
```

---

## Agent ↔ Proxy Protocol (Agent Server)

### Agent → Proxy Messages

#### `agent.register`
First message sent after connection. agentToken must match proxy config OR server-specific stored token.
```json
{
  "type":"agent.register",
  "agentToken":"the-agent-token-from-proxy-config",
  "payload":{
    "serverId":"survival",
    "serverName":"Survival",
    "serverType":"survival",
    "host":"localhost",
    "port":25566
  }
}
```

#### `agent.heartbeat`
Sent every N seconds (configurable, default 10s):
```json
{
  "type":"agent.heartbeat",
  "serverId":"survival",
  "payload":{
    "tps":19.98,
    "mspt":2.5,
    "onlinePlayers":12,
    "maxPlayers":100
  }
}
```

#### `console.output`
Sent for each line of server console output:
```json
{
  "type":"console.output",
  "serverId":"survival",
  "payload":{
    "line":"[00:00:00 INFO]: Done (2.5s)! For help, type \"help\"",
    "timestamp":1700000000000
  }
}
```

#### `players.update`
Sent periodically and when player list changes:
```json
{
  "type":"players.update",
  "serverId":"survival",
  "payload":{
    "players":[
      {
        "uuid":"550e8400-e29b-41d4-a716-446655440000",
        "name":"Notch",
        "serverId":"survival",
        "world":"world",
        "gamemode":"SURVIVAL",
        "health":20.0,
        "foodLevel":20,
        "ping":42,
        "isOnline":true,
        "firstJoined":1690000000000,
        "lastJoined":1700000000000,
        "playtimeSeconds":36000
      }
    ]
  }
}
```

### Proxy → Agent Messages

#### `agent.registered`
Confirmation after successful registration:
```json
{"type":"agent.registered","success":true,"message":"Registered as survival"}
```

If registration fails:
```json
{"type":"agent.registered","success":false,"message":"Invalid agent token"}
```

#### `console.command`
Execute a command on the server:
```json
{"type":"console.command","payload":{"command":"say Hello from staff!"}}
```

#### `server.info.request`
Request full server info (agent responds with heartbeat + players.update):
```json
{"type":"server.info.request"}
```

#### `players.request`
Request current player list (agent responds with players.update):
```json
{"type":"players.request"}
```

---

## Error Codes

| Code | Description |
|---|---|
| `UNAUTHORIZED` | No valid session token |
| `FORBIDDEN` | Missing required permission |
| `SETUP_ALREADY_DONE` | Owner already exists |
| `INVALID_CREDENTIALS` | Wrong username or password |
| `ACCOUNT_DISABLED` | User account is inactive |
| `SERVER_NOT_FOUND` | Server ID not in database |
| `SERVER_ALREADY_EXISTS` | Server ID already registered |
| `SERVER_OFFLINE` | Agent not connected |
| `USER_NOT_FOUND` | User ID not in database |
| `USER_ALREADY_EXISTS` | Username already taken |
| `ROLE_NOT_FOUND` | Role ID not in database |
| `CANNOT_DELETE_SELF` | Cannot delete your own account |
| `CANNOT_DELETE_SYSTEM_ROLE` | System roles cannot be deleted |
| `INSUFFICIENT_PRIVILEGES` | Operation requires higher privilege |
| `VALIDATION_ERROR` | Input validation failed |
| `EMPTY_COMMAND` | Command cannot be empty |
| `INVALID_AGENT_TOKEN` | Agent token mismatch |
| `AGENT_NOT_REGISTERED` | Agent sent message before registering |
| `INTERNAL_ERROR` | Unexpected server error |
