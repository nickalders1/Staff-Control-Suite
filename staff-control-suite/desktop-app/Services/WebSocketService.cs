using System.Collections.Concurrent;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;

namespace StaffControlSuite.Services;

public class WebSocketService
{
    private ClientWebSocket? _ws;
    private CancellationTokenSource? _cts;

    private readonly ConcurrentDictionary<string, TaskCompletionSource<JsonElement>> _pendingRequests = new();

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public bool IsConnected => _ws?.State == WebSocketState.Open;

    public void InjectPlayerJoin(PlayerInfo player) =>
        PlayerJoinReceived?.Invoke(player, null);

    // Fired for every incoming console line (serverId, rawLine, timestampMs)
    public event Action<string, string, long>? ConsoleLineReceived;

    // Fired when server status changes (serverId, online, playerCount, tps, mspt, lastHeartbeat)
    public event Action<string, bool, int, double, double, long>? ServerStatusChanged;

    // Fired when an agent's connection status changes
    public event Action<Models.AgentStatus>? AgentStatusChanged;

    // Fired for periodic full player-list syncs from heartbeat (serverId, players)
    public event Action<string, List<PlayerInfo>>? PlayerUpdateReceived;

    // Fired when a player joins or switches to a server (player, previousServerId-if-switch)
    public event Action<PlayerInfo, string?>? PlayerJoinReceived;

    // Fired when a player leaves the network (uuid, serverId)
    public event Action<string, string>? PlayerLeftReceived;

    public event Action? Connected;
    public event Action? Disconnected;

    // Fired when a new punishment is created on the server
    public event Action<Punishment>? PunishmentCreated;

    // Fired when a punishment is revoked (provides punishmentId)
    public event Action<long>? PunishmentRevoked;

    public async Task ConnectAsync(string host, int port)
    {
        if (_ws != null && _ws.State == WebSocketState.Open)
            await DisconnectAsync();

        _cts = new CancellationTokenSource();
        _ws  = new ClientWebSocket();

        await _ws.ConnectAsync(new Uri($"ws://{host}:{port}"), _cts.Token);
        _ = Task.Run(() => ReceiveLoopAsync(_cts.Token), _cts.Token);
        Connected?.Invoke();
    }

    public async Task DisconnectAsync()
    {
        try
        {
            _cts?.Cancel();
            if (_ws != null && _ws.State == WebSocketState.Open)
                await _ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "Disconnecting", CancellationToken.None);
        }
        catch { }
        finally
        {
            _ws?.Dispose();
            _ws = null;
            foreach (var kvp in _pendingRequests)
                kvp.Value.TrySetException(new Exception("WebSocket disconnected."));
            _pendingRequests.Clear();
            Disconnected?.Invoke();
        }
    }

    public async Task<JsonElement> SendRequestAsync(string type, object? payload = null,
                                                     string? token = null, int timeoutMs = 10000)
    {
        if (_ws == null || _ws.State != WebSocketState.Open)
            throw new InvalidOperationException("WebSocket is not connected.");

        var requestId = Guid.NewGuid().ToString();
        var tcs = new TaskCompletionSource<JsonElement>();
        _pendingRequests[requestId] = tcs;

        try
        {
            var message = new { type, requestId, token, payload };
            var bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(message, JsonOptions));
            await _ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true,
                                _cts?.Token ?? CancellationToken.None);

            using var timeoutCts = new CancellationTokenSource(timeoutMs);
            timeoutCts.Token.Register(() =>
                tcs.TrySetException(new TimeoutException($"Request '{type}' timed out after {timeoutMs}ms.")));

            return await tcs.Task;
        }
        catch
        {
            _pendingRequests.TryRemove(requestId, out _);
            throw;
        }
    }

    private async Task ReceiveLoopAsync(CancellationToken ct)
    {
        var buffer = new byte[65536];
        try
        {
            while (!ct.IsCancellationRequested && _ws != null && _ws.State == WebSocketState.Open)
            {
                var sb = new StringBuilder();
                WebSocketReceiveResult result;
                do
                {
                    result = await _ws.ReceiveAsync(new ArraySegment<byte>(buffer), ct);
                    if (result.MessageType == WebSocketMessageType.Close) { Disconnected?.Invoke(); return; }
                    sb.Append(Encoding.UTF8.GetString(buffer, 0, result.Count));
                }
                while (!result.EndOfMessage);

                var raw = sb.ToString();
                if (!string.IsNullOrWhiteSpace(raw))
                {
                    try { ProcessMessage(raw); } catch { }
                }
            }
        }
        catch (OperationCanceledException) { }
        catch
        {
            foreach (var kvp in _pendingRequests)
                kvp.Value.TrySetException(new Exception("WebSocket connection lost."));
            _pendingRequests.Clear();
            Disconnected?.Invoke();
        }
    }

    private void ProcessMessage(string raw)
    {
        using var doc = JsonDocument.Parse(raw);
        var root = doc.RootElement;
        if (!root.TryGetProperty("type", out var typeProp)) return;

        switch (typeProp.GetString() ?? "")
        {
            case MessageTypes.Response:           HandleResponse(root);      break;
            case MessageTypes.EventConsoleLine:       HandleConsoleLine(root);       break;
            case MessageTypes.EventServerStatus:      HandleServerStatus(root);      break;
            case MessageTypes.EventPlayerUpdate:      HandlePlayerUpdate(root);      break;
            case MessageTypes.EventAgentStatus:       HandleAgentStatus(root);       break;
            case "event.player.join":                 HandlePlayerJoin(root);        break;
            case "event.player.leave":                HandlePlayerLeft(root);        break;
            case MessageTypes.EventPunishmentCreated: HandlePunishmentCreated(root); break;
            case MessageTypes.EventPunishmentRevoked: HandlePunishmentRevoked(root); break;
        }
    }

    private void HandleResponse(JsonElement root)
    {
        if (!root.TryGetProperty("requestId", out var reqIdProp)) return;
        var requestId = reqIdProp.GetString() ?? "";
        if (!_pendingRequests.TryRemove(requestId, out var tcs)) return;

        var success = root.TryGetProperty("success", out var sp) && sp.GetBoolean();
        if (success)
        {
            var payload = root.TryGetProperty("payload", out var pp) ? pp.Clone() : default;
            tcs.TrySetResult(payload);
        }
        else
        {
            var errorCode = root.TryGetProperty("error",   out var ep) ? ep.GetString() : null;
            var message   = root.TryGetProperty("message", out var mp) ? mp.GetString() : null;
            tcs.TrySetException(new Exception(message ?? errorCode ?? "Unknown error from server."));
        }
    }

    private void HandleConsoleLine(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var payload)) return;
        var serverId  = payload.TryGetProperty("serverId",  out var sid) ? sid.GetString() ?? "" : "";
        var line      = payload.TryGetProperty("line",      out var l)   ? l.GetString()   ?? "" : "";
        var timestamp = payload.TryGetProperty("timestamp", out var ts)  ? ts.GetInt64()        : 0L;
        ConsoleLineReceived?.Invoke(serverId, line, timestamp);
    }

    private void HandleServerStatus(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var payload)) return;
        var serverId      = payload.TryGetProperty("serverId",      out var sid) ? sid.GetString() ?? "" : "";
        var online        = payload.TryGetProperty("online",        out var o)  && o.GetBoolean();
        var playerCount   = payload.TryGetProperty("playerCount",   out var pc) ? pc.GetInt32()        : 0;
        var tps           = payload.TryGetProperty("tps",           out var t)  ? t.GetDouble()         : 0.0;
        var mspt          = payload.TryGetProperty("mspt",          out var m)  ? m.GetDouble()         : 0.0;
        var lastHeartbeat = payload.TryGetProperty("lastHeartbeat", out var lh) ? lh.GetInt64()         : 0L;
        ServerStatusChanged?.Invoke(serverId, online, playerCount, tps, mspt, lastHeartbeat);
    }

    private void HandlePlayerUpdate(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var payload)) return;
        var serverId = payload.TryGetProperty("serverId", out var sid) ? sid.GetString() ?? "" : "";
        var players  = new List<PlayerInfo>();
        if (payload.TryGetProperty("players", out var arr))
            foreach (var p in arr.EnumerateArray())
                players.Add(ParsePlayerInfo(p));
        PlayerUpdateReceived?.Invoke(serverId, players);
    }

    private void HandlePlayerJoin(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var payload)) return;
        var player         = ParsePlayerInfo(payload);
        var isSwitch       = payload.TryGetProperty("isSwitch",      out var sw) && sw.GetBoolean();
        var previousServer = payload.TryGetProperty("previousServer", out var ps) ? ps.GetString() : null;
        PlayerJoinReceived?.Invoke(player, isSwitch ? previousServer : null);
    }

    private void HandleAgentStatus(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var p)) return;
        var agent = new Models.AgentStatus
        {
            ServerId          = p.TryGetProperty("serverId",          out var sid) ? sid.GetString() ?? "" : "",
            Connected         = p.TryGetProperty("connected",         out var c)   && c.GetBoolean(),
            Status            = p.TryGetProperty("status",            out var s)   ? s.GetString() ?? "OFFLINE" : "OFFLINE",
            Version           = p.TryGetProperty("version",           out var v)   ? v.GetString() ?? "" : "",
            ProtocolVersion   = p.TryGetProperty("protocolVersion",   out var pv)  ? pv.GetInt32()  : 0,
            LastHeartbeatAt   = p.TryGetProperty("lastHeartbeatAt",   out var lh)  ? lh.GetInt64()  : 0L,
            LatencyMs         = p.TryGetProperty("latencyMs",         out var lm)  ? lm.GetInt64()  : 0L,
            ConnectTime       = p.TryGetProperty("connectTime",       out var ct)  ? ct.GetInt64()  : 0L,
            ReconnectAttempts = p.TryGetProperty("reconnectAttempts", out var ra)  ? ra.GetInt32()  : 0,
            LastError         = p.TryGetProperty("lastError",         out var le)  ? le.GetString() ?? "" : "",
        };
        agent.RefreshTimeDisplays();
        AgentStatusChanged?.Invoke(agent);
    }

    private void HandlePlayerLeft(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var payload)) return;
        var uuid     = payload.TryGetProperty("uuid",     out var u)   ? u.GetString()   ?? "" : "";
        var serverId = payload.TryGetProperty("serverId", out var sid) ? sid.GetString() ?? "" : "";
        PlayerLeftReceived?.Invoke(uuid, serverId);
    }

    private void HandlePunishmentCreated(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var payload)) return;
        var punishment = Punishment.FromJson(payload);
        PunishmentCreated?.Invoke(punishment);
    }

    private void HandlePunishmentRevoked(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var payload)) return;
        var punishmentId = payload.TryGetProperty("punishmentId", out var pid) ? pid.GetInt64() : 0L;
        if (punishmentId > 0) PunishmentRevoked?.Invoke(punishmentId);
    }

    private static PlayerInfo ParsePlayerInfo(JsonElement p) => new()
    {
        Uuid            = p.TryGetProperty("uuid",            out var u)   ? u.GetString()   ?? "" : "",
        Name            = p.TryGetProperty("name",            out var n)   ? n.GetString()   ?? "" : "",
        ServerId        = p.TryGetProperty("serverId",        out var sid) ? sid.GetString() ?? "" : "",
        World           = p.TryGetProperty("world",           out var w)   ? w.GetString()   ?? "" : "",
        Gamemode        = p.TryGetProperty("gamemode",        out var gm)  ? gm.GetString()  ?? "" : "",
        Health          = p.TryGetProperty("health",          out var h)   ? h.GetDouble()        : 20.0,
        FoodLevel       = p.TryGetProperty("foodLevel",       out var fl)  ? fl.GetInt32()        : 20,
        Ping            = p.TryGetProperty("ping",            out var pg)  ? pg.GetInt32()        : 0,
        IsOnline        = p.TryGetProperty("isOnline",        out var io)  && io.GetBoolean(),
        FirstJoined     = p.TryGetProperty("firstJoined",     out var fj)  ? fj.GetInt64()        : 0L,
        LastJoined      = p.TryGetProperty("lastJoined",      out var lj)  ? lj.GetInt64()        : 0L,
        PlaytimeSeconds = p.TryGetProperty("playtimeSeconds", out var pt)  ? pt.GetInt64()        : 0L
    };
}
