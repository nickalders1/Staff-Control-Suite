namespace StaffControlSuite.Models;

public sealed class ConsoleEntry
{
    public string ServerId { get; }
    public string Line { get; }
    public long TimestampMs { get; }
    public string Formatted { get; }

    public ConsoleEntry(string serverId, string line, long timestampMs)
    {
        ServerId = serverId;
        Line = line;
        TimestampMs = timestampMs;
        var time = DateTimeOffset.FromUnixTimeMilliseconds(timestampMs > 0 ? timestampMs : DateTimeOffset.UtcNow.ToUnixTimeMilliseconds())
                                 .LocalDateTime;
        Formatted = $"[{time:HH:mm:ss}] {line}";
    }
}
