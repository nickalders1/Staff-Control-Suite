using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Text.Json;

namespace StaffControlSuite.Models;

public class PunishmentPreset : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;
    private void NotifyChanged([CallerMemberName] string? name = null)
        => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));

    public long   Id               { get; set; }
    public string Category         { get; set; } = "";
    public string Name             { get; set; } = "";
    public string Description      { get; set; } = "";
    public string ActionType       { get; set; } = "";
    public long   DurationSeconds  { get; set; }
    public int    Severity         { get; set; }
    public bool   Stackable        { get; set; }
    public bool   BypassCap        { get; set; }
    public bool   RequiresIpBan    { get; set; }
    public bool   Enabled          { get; set; }

    private bool _isSelected;
    public bool IsSelected
    {
        get => _isSelected;
        set { if (_isSelected != value) { _isSelected = value; NotifyChanged(); } }
    }

    public string DurationDisplay => Punishment.FormatDuration(DurationSeconds);

    public static PunishmentPreset FromJson(JsonElement e) => new()
    {
        Id              = e.TryGetProperty("id",              out var id)  ? id.GetInt64()        : 0,
        Category        = e.TryGetProperty("category",        out var cat) ? cat.GetString() ?? "" : "",
        Name            = e.TryGetProperty("name",            out var n)   ? n.GetString()   ?? "" : "",
        Description     = e.TryGetProperty("description",     out var d)   ? d.GetString()   ?? "" : "",
        ActionType      = e.TryGetProperty("actionType",      out var at)  ? at.GetString()  ?? "" : "",
        DurationSeconds = e.TryGetProperty("durationSeconds", out var ds)  ? ds.GetInt64()        : 0,
        Severity        = e.TryGetProperty("severity",        out var sv)  ? sv.GetInt32()        : 1,
        Stackable       = e.TryGetProperty("stackable",       out var sk)  && sk.GetBoolean(),
        BypassCap       = e.TryGetProperty("bypassCap",       out var bc)  && bc.GetBoolean(),
        RequiresIpBan   = e.TryGetProperty("requiresIpBan",   out var rib) && rib.GetBoolean(),
        Enabled         = e.TryGetProperty("enabled",         out var en)  && en.GetBoolean(),
    };
}
