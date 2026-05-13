using StaffControlSuite.Models;

namespace StaffControlSuite.Services;

public sealed class ConsoleLogService
{
    private const int MaxLinesPerServer = 10_000;

    private readonly Dictionary<string, LinkedList<ConsoleEntry>> _buffers = new();
    private readonly ReaderWriterLockSlim _lock = new();

    public event Action<string, ConsoleEntry>? LineAdded;

    public ConsoleLogService(WebSocketService ws)
    {
        ws.ConsoleLineReceived += Append;
    }

    public void Append(string serverId, string line, long timestampMs)
    {
        var entry = new ConsoleEntry(serverId, line, timestampMs);

        _lock.EnterWriteLock();
        try
        {
            if (!_buffers.TryGetValue(serverId, out var buffer))
            {
                buffer = new LinkedList<ConsoleEntry>();
                _buffers[serverId] = buffer;
            }
            buffer.AddLast(entry);
            while (buffer.Count > MaxLinesPerServer)
                buffer.RemoveFirst();
        }
        finally
        {
            _lock.ExitWriteLock();
        }

        LineAdded?.Invoke(serverId, entry);
    }

    public IReadOnlyList<ConsoleEntry> GetBuffer(string serverId)
    {
        _lock.EnterReadLock();
        try
        {
            if (_buffers.TryGetValue(serverId, out var buffer))
                return buffer.ToList();
            return Array.Empty<ConsoleEntry>();
        }
        finally
        {
            _lock.ExitReadLock();
        }
    }

    public void ClearBuffer(string serverId)
    {
        _lock.EnterWriteLock();
        try { _buffers.Remove(serverId); }
        finally { _lock.ExitWriteLock(); }
    }
}
