using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MaterialDesignThemes.Wpf;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;

namespace StaffControlSuite.ViewModels;

public partial class CreatePunishmentViewModel : ObservableObject
{
    public ObservableCollection<PunishmentPreset> Presets         { get; } = new();
    public ObservableCollection<PunishmentPreset> SelectedPresets { get; } = new();
    public ObservableCollection<string>           ActionTypes     { get; } = new();

    [ObservableProperty] private string _targetName                = "";
    [ObservableProperty] private string _selectedActionType        = "";
    [ObservableProperty] private string _reason                    = "";
    [ObservableProperty] private string _evidence                  = "";
    [ObservableProperty] private string _durationDisplay           = "Permanent";
    [ObservableProperty] private long   _calculatedDurationSeconds = 0;
    [ObservableProperty] private bool   _isPermanent               = true;
    [ObservableProperty] private bool   _requiresIpBan             = false;
    [ObservableProperty] private string _targetServer              = "global";
    [ObservableProperty] private string _manualDurationText        = "";
    [ObservableProperty] private bool   _isLoading                 = false;
    [ObservableProperty] private string _errorMessage              = "";

    public bool CanOverrideDuration =>
        App.AuthService.HasPermission("moderation.override_duration") || App.AuthService.IsOwner();

    public bool CanBan     => App.AuthService.HasPermission("moderation.ban")  || App.AuthService.IsOwner();
    public bool CanMute    => App.AuthService.HasPermission("moderation.mute") || App.AuthService.IsOwner();
    public bool CanWarn    => App.AuthService.HasPermission("moderation.warn") || App.AuthService.IsOwner();
    public bool CanKick    => App.AuthService.HasPermission("moderation.kick") || App.AuthService.IsOwner();

    // Summary for preview section
    public string PunishmentSummary =>
        $"{SelectedActionType} | {DurationDisplay} | {(string.IsNullOrWhiteSpace(Reason) ? "(no reason)" : Reason)}";

    private Action<Punishment?>? _closeCallback;

    public CreatePunishmentViewModel(string targetName, Action<Punishment?>? closeCallback = null)
    {
        TargetName     = targetName;
        _closeCallback = closeCallback;
        BuildActionTypes();
    }

    private void BuildActionTypes()
    {
        ActionTypes.Clear();
        if (CanWarn)    ActionTypes.Add("WARN");
        if (CanKick)    ActionTypes.Add("KICK");
        if (CanMute)  { ActionTypes.Add("MUTE"); ActionTypes.Add("TEMP_MUTE"); }
        if (CanBan)   { ActionTypes.Add("BAN");  ActionTypes.Add("TEMP_BAN"); ActionTypes.Add("IP_BAN"); ActionTypes.Add("TEMP_IP_BAN"); }

        if (ActionTypes.Count > 0)
            SelectedActionType = ActionTypes[0];
    }

    [RelayCommand]
    public async Task LoadPresetsAsync()
    {
        IsLoading = true;
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationPresetsList, null, App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                Presets.Clear();
                JsonElement arr = default;
                if (result.TryGetProperty("presets", out var pa)) arr = pa;
                else if (result.ValueKind == JsonValueKind.Array)  arr = result;

                if (arr.ValueKind == JsonValueKind.Array)
                    foreach (var item in arr.EnumerateArray())
                    {
                        var preset = PunishmentPreset.FromJson(item);
                        if (preset.Enabled) Presets.Add(preset);
                    }
            });
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsLoading = false; }
    }

    [RelayCommand]
    public async Task TogglePresetAsync(PunishmentPreset? preset)
    {
        if (preset == null) return;

        preset.IsSelected = !preset.IsSelected;
        if (preset.IsSelected)
            SelectedPresets.Add(preset);
        else
            SelectedPresets.Remove(preset);

        OnPropertyChanged(nameof(Presets));
        await RecalculateAsync();
    }

    [RelayCommand]
    public async Task RecalculateAsync()
    {
        if (SelectedPresets.Count == 0)
        {
            CalculatedDurationSeconds = 0;
            DurationDisplay           = "Permanent";
            IsPermanent               = true;
            RequiresIpBan             = false;
            return;
        }

        var presetIds = SelectedPresets.Select(p => p.Id).ToArray();
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationCalculate,
                new { targetName = TargetName, presetIds },
                App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                if (result.TryGetProperty("actionType",      out var at))  SelectedActionType        = at.GetString() ?? SelectedActionType;
                if (result.TryGetProperty("durationSeconds", out var ds))  CalculatedDurationSeconds = ds.GetInt64();
                if (result.TryGetProperty("requiresIpBan",   out var rib)) RequiresIpBan             = rib.GetBoolean();

                IsPermanent     = CalculatedDurationSeconds <= 0;
                DurationDisplay = Punishment.FormatDuration(CalculatedDurationSeconds);

                OnPropertyChanged(nameof(PunishmentSummary));
            });
        }
        catch { /* best-effort */ }
    }

    private long ParseManualDuration()
    {
        if (string.IsNullOrWhiteSpace(ManualDurationText)) return 0;
        var text = ManualDurationText.Trim().ToLower();
        long total = 0;
        var i = 0;
        while (i < text.Length)
        {
            var numStart = i;
            while (i < text.Length && char.IsDigit(text[i])) i++;
            if (i == numStart) { i++; continue; }
            if (!long.TryParse(text[numStart..i], out var num)) continue;
            if (i >= text.Length) break;
            var unit = text[i]; i++;
            total += unit switch
            {
                's' => num,
                'm' => num * 60,
                'h' => num * 3600,
                'd' => num * 86400,
                'w' => num * 604800,
                _   => 0
            };
        }
        return total;
    }

    [RelayCommand]
    public async Task ConfirmAsync()
    {
        ErrorMessage = "";
        if (string.IsNullOrWhiteSpace(Reason))
        {
            ErrorMessage = "Reason is required.";
            return;
        }
        if (string.IsNullOrWhiteSpace(SelectedActionType))
        {
            ErrorMessage = "Action type is required.";
            return;
        }

        var durationSecs = CanOverrideDuration && !string.IsNullOrWhiteSpace(ManualDurationText)
            ? ParseManualDuration()
            : CalculatedDurationSeconds;

        IsLoading = true;
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationPunish,
                new
                {
                    targetName   = TargetName,
                    actionType   = SelectedActionType,
                    reason       = Reason,
                    evidence     = Evidence,
                    targetServer = TargetServer,
                    durationSeconds = durationSecs,
                    requiresIpBan = RequiresIpBan,
                    presetIds    = SelectedPresets.Select(p => p.Id).ToArray()
                },
                App.AuthService.SessionToken);

            var punishment = result.ValueKind != JsonValueKind.Undefined
                ? Punishment.FromJson(result)
                : null;

            _closeCallback?.Invoke(punishment);
            DialogHost.Close("RootDialogHost", punishment);
        }
        catch (Exception ex)
        {
            ErrorMessage = ex.Message;
        }
        finally { IsLoading = false; }
    }

    [RelayCommand]
    public void Cancel()
    {
        _closeCallback?.Invoke(null);
        DialogHost.Close("RootDialogHost", null);
    }

    partial void OnSelectedActionTypeChanged(string value)
        => OnPropertyChanged(nameof(PunishmentSummary));

    partial void OnReasonChanged(string value)
        => OnPropertyChanged(nameof(PunishmentSummary));

    partial void OnDurationDisplayChanged(string value)
        => OnPropertyChanged(nameof(PunishmentSummary));
}
