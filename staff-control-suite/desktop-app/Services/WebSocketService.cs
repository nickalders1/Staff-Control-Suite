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

    public event Action<string, string, long>? ConsoleLineReceived;
    public event Action<string, bool, int, double, double, long>? ServerStatusChanged;
    public event Action<string, List<PlayerInfo>>? PlayerUpdateReceived;
    public event Action? Connected;
    public event Action? Disconnected;

    public async Task ConnectAsync(string host, int port)
    {
        if (_ws != null && _ws.State == WebSocketState.Open)
        {
            await DisconnectAsync();
        }

        _cts = new CancellationTokenSource();
        _ws = new ClientWebSocket();

        var uri = new Uri($"ws://{host}:{port}");
        await _ws.ConnectAsync(uri, _cts.Token);

        _ = Task.Run(() => ReceiveLoopAsync(_cts.Token), _cts.Token);

        Connected?.Invoke();
    }

    public async Task DisconnectAsync()
    {
        try
        {
            _cts?.Cancel();

            if (_ws != null && _ws.State == WebSocketState.Open)
            {
                await _ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "Disconnecting", CancellationToken.None);
            }
        }
        catch
        {
            // Ignore errors during disconnect
        }
        finally
        {
            _ws?.Dispose();
            _ws = null;

            // Complete all pending requests with an exception
            foreach (var kvp in _pendingRequests)
            {
                kvp.Value.TrySetException(new Exception("WebSocket disconnected."));
            }
            _pendingRequests.Clear();

            Disconnected?.Invoke();
        }
    }

    public async Task<JsonElement> SendRequestAsync(string type, object? payload = null, string? token = null, int timeoutMs = 10000)
    {
        if (_ws == null || _ws.State != WebSocketState.Open)
            throw new InvalidOperationException("WebSocket is not connected.");

        var requestId = Guid.NewGuid().ToString();
        var tcs = new TaskCompletionSource<JsonElement>();
        _pendingRequests[requestId] = tcs;

        try
        {
            var message = new
            {
                type,
                requestId,
                token,
                payload
            };

            var json = JsonSerializer.Serialize(message, JsonOptions);
            var bytes = Encoding.UTF8.GetBytes(json);

            await _ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, _cts?.Token ?? CancellationToken.None);

            using var cts = new CancellationTokenSource(timeoutMs);
            cts.Token.Register(() => tcs.TrySetException(new TimeoutException($"Request '{type}' timed out after {timeoutMs}ms.")));

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

                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        Disconnected?.Invoke();
                        return;
                    }

                    sb.Append(Encoding.UTF8.GetString(buffer, 0, result.Count));
                }
                while (!result.EndOfMessage);

                var raw = sb.ToString();
                if (string.IsNullOrWhiteSpace(raw)) continue;

                try
                {
                    ProcessMessage(raw);
                }
                catch
                {
                    // Ignore malformed messages
                }
            }
        }
        catch (OperationCanceledException)
        {
            // Normal on shutdown
        }
        catch
        {
            // Connection dropped
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
        var type = typeProp.GetString() ?? "";

        switch (type)
        {
            case MessageTypes.Response:
                HandleResponse(root);
                break;

            case MessageTypes.EventConsoleLine:
                HandleConsoleLine(root);
                break;

            case MessageTypes.EventServerStatus:
                HandleServerStatus(root);
                break;

            case MessageTypes.EventPlayerUpdate:
                HandlePlayerUpdate(root);
                break;
        }
    }

    private void HandleResponse(JsonElement root)
    {
        if (!root.TryGetProperty("requestId", out var reqIdProp)) return;
        var requestId = reqIdProp.GetString() ?? "";

        if (!_pendingRequests.TryRemove(requestId, out var tcs)) return;

        var success = root.TryGetProperty("success", out var successProp) && successProp.GetBoolean();

        if (success)
        {
            var payload = root.TryGetProperty("payload", out var payloadProp)
                ? payloadProp.Clone()
                : default;
            tcs.TrySetResult(payload);
        }
        else
        {
            var errorCode = root.TryGetProperty("error", out var errProp) ? errProp.GetString() : null;
            var message = root.TryGetProperty("message", out var msgProp) ? msgProp.GetString() : null;
            tcs.TrySetException(new Exception(message ?? errorCode ?? "Unknown error from server."));
        }
    }

    private void HandleConsoleLine(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var payload)) return;

        var serverId = payload.TryGetProperty("serverId", out var sid) ? sid.GetString() ?? "" : "";
        var line = payload.TryGetProperty("line", out var l) ? l.GetString() ?? "" : "";
        var timestamp = payload.TryGetProperty("timestamp", out var ts) ? ts.GetInt64() : 0L;

        ConsoleLineReceived?.Invoke(serverId, line, timestamp);
    }

    private void HandleServerStatus(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var payload)) return;

        var serverId = payload.TryGetProperty("serverId", out var sid) ? sid.GetString() ?? "" : "";
        var online = payload.TryGetProperty("online", out var o) && o.GetBoolean();
        var playerCount = payload.TryGetProperty("playerCount", out var pc) ? pc.GetInt32() : 0;
        var tps = payload.TryGetProperty("tps", out var t) ? t.GetDouble() : 0.0;
        var mspt = payload.TryGetProperty("mspt", out var m) ? m.GetDouble() : 0.0;
        var lastHeartbeat = payload.TryGetProperty("lastHeartbeat", out var lh) ? lh.GetInt64() : 0L;

        ServerStatusChanged?.Invoke(serverId, online, playerCount, tps, mspt, lastHeartbeat);
    }

    private void HandlePlayerUpdate(JsonElement root)
    {
        if (!root.TryGetProperty("payload", out var payload)) return;

        var serverId = payload.TryGetProperty("serverId", out var sid) ? sid.GetString() ?? "" : "";
        var players = new List<PlayerInfo>();

        if (payload.TryGetProperty("players", out var playersArr))
        {
            foreach (var p in playersArr.EnumerateArray())
            {
                players.Add(ParsePlayerInfo(p));
            }
        }

        PlayerUpdateReceived?.Invoke(serverId, players);
    }

    private static PlayerInfo ParsePlayerInfo(JsonElement p)
    {
        return new PlayerInfo
        {
            Uuid = p.TryGetProperty("uuid", out var u) ? u.GetString() ?? "" : "",
            Name = p.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "",
            ServerId = p.TryGetProperty("serverId", out var sid) ? sid.GetString() ?? "" : "",
            World = p.TryGetProperty("world", out var w) ? w.GetString() ?? "" : "",
            Gamemode = p.TryGetProperty("gamemode", out var gm) ? gm.GetString() ?? "" : "",
            Health = p.TryGetProperty("health", out var h) ? h.GetDouble() : 20.0,
            FoodLevel = p.TryGetProperty("foodLevel", out var fl) ? fl.GetInt32() : 20,
            Ping = p.TryGetProperty("ping", out var ping) ? ping.GetInt32() : 0,
            IsOnline = p.TryGetProperty("isOnline", out var io) && io.GetBoolean(),
            FirstJoined = p.TryGetProperty("firstJoined", out var fj) ? fj.GetInt64() : 0L,
            LastJoined = p.TryGetProperty("lastJoined", out var lj) ? lj.GetInt64() : 0L,
            PlaytimeSeconds = p.TryGetProperty("playtimeSeconds", out var pt) ? pt.GetInt64() : 0L
        };
    }
}
