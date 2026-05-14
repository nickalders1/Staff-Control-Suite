using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MaterialDesignThemes.Wpf;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;
using StaffControlSuite.Views.Dialogs;

namespace StaffControlSuite.ViewModels;

public partial class PresetManagerViewModel : ObservableObject
{
    public ObservableCollection<PunishmentPreset> Presets         { get; } = new();
    public ObservableCollection<PunishmentPreset> FilteredPresets { get; } = new();
    public ObservableCollection<string>           Categories      { get; } = new();

    [ObservableProperty] private bool   _isLoading;
    [ObservableProperty] private string _errorMessage    = "";
    [ObservableProperty] private string _newCategory     = "";
    [ObservableProperty] private string _newName         = "";
    [ObservableProperty] private string _newDescription  = "";
    [ObservableProperty] private string _newActionType   = "WARN";
    [ObservableProperty] private string _newDurationText = "0";
    [ObservableProperty] private int    _newSeverity     = 1;
    [ObservableProperty] private bool   _newStackable    = true;
    [ObservableProperty] private bool   _newBypassCap    = false;
    [ObservableProperty] private bool   _newRequiresIpBan = false;
    [ObservableProperty] private bool   _isCreating      = false;

    public ObservableCollection<string> ActionTypeOptions { get; } = new()
    {
        "WARN", "KICK", "MUTE", "TEMP_MUTE", "BAN", "TEMP_BAN", "IP_BAN", "TEMP_IP_BAN"
    };

    public bool CanManage =>
        App.AuthService.HasPermission("moderation.presets.manage") || App.AuthService.IsOwner();

    private string _selectedCategory = "All";
    public string SelectedCategory
    {
        get => _selectedCategory;
        set
        {
            if (SetProperty(ref _selectedCategory, value))
                ApplyFilter();
        }
    }

    [RelayCommand]
    public async Task LoadPresetsAsync()
    {
        IsLoading    = true;
        ErrorMessage = "";
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationPresetsList, null, App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                Presets.Clear();
                Categories.Clear();
                Categories.Add("All");

                JsonElement arr = default;
                if (result.TryGetProperty("presets", out var pa)) arr = pa;
                else if (result.ValueKind == JsonValueKind.Array)  arr = result;

                if (arr.ValueKind == JsonValueKind.Array)
                    foreach (var item in arr.EnumerateArray())
                    {
                        var preset = PunishmentPreset.FromJson(item);
                        Presets.Add(preset);
                        if (!Categories.Contains(preset.Category) && !string.IsNullOrWhiteSpace(preset.Category))
                            Categories.Add(preset.Category);
                    }

                ApplyFilter();
            });
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsLoading = false; }
    }

    private void ApplyFilter()
    {
        FilteredPresets.Clear();
        foreach (var p in Presets)
        {
            if (SelectedCategory == "All" || p.Category == SelectedCategory)
                FilteredPresets.Add(p);
        }
    }

    [RelayCommand]
    public async Task CreatePresetAsync()
    {
        if (!CanManage) return;
        ErrorMessage = "";
        if (string.IsNullOrWhiteSpace(NewName))     { ErrorMessage = "Name is required.";     return; }
        if (string.IsNullOrWhiteSpace(NewCategory)) { ErrorMessage = "Category is required."; return; }

        long durationSecs = 0;
        if (!string.IsNullOrWhiteSpace(NewDurationText) && !long.TryParse(NewDurationText, out durationSecs))
            durationSecs = 0;

        IsCreating = true;
        try
        {
            await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationPresetsCreate,
                new
                {
                    category        = NewCategory,
                    name            = NewName,
                    description     = NewDescription,
                    actionType      = NewActionType,
                    durationSeconds = durationSecs,
                    severity        = NewSeverity,
                    stackable       = NewStackable,
                    bypassCap       = NewBypassCap,
                    requiresIpBan   = NewRequiresIpBan,
                    enabled         = true
                },
                App.AuthService.SessionToken);

            // Reset form
            NewName         = "";
            NewDescription  = "";
            NewDurationText = "0";
            NewSeverity     = 1;
            NewStackable    = true;
            NewBypassCap    = false;
            NewRequiresIpBan = false;

            await LoadPresetsAsync();
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsCreating = false; }
    }

    [RelayCommand]
    public async Task DeletePresetAsync(long id)
    {
        if (!CanManage) return;
        var confirm = await DialogHost.Show(
            new ConfirmDialog("Delete preset?", "This will permanently remove this preset."),
            "RootDialogHost");
        if (confirm is not true) return;

        IsLoading = true;
        try
        {
            await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationPresetsDelete,
                new { id },
                App.AuthService.SessionToken);
            await LoadPresetsAsync();
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsLoading = false; }
    }

    [RelayCommand]
    public async Task ToggleEnabledAsync(PunishmentPreset? preset)
    {
        if (!CanManage || preset == null) return;
        try
        {
            await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationPresetsUpdate,
                new
                {
                    id              = preset.Id,
                    category        = preset.Category,
                    name            = preset.Name,
                    description     = preset.Description,
                    actionType      = preset.ActionType,
                    durationSeconds = preset.DurationSeconds,
                    severity        = preset.Severity,
                    stackable       = preset.Stackable,
                    bypassCap       = preset.BypassCap,
                    requiresIpBan   = preset.RequiresIpBan,
                    enabled         = !preset.Enabled
                },
                App.AuthService.SessionToken);
            preset.Enabled = !preset.Enabled;
            ApplyFilter();
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
    }

    [RelayCommand]
    public async Task EditPresetAsync(PunishmentPreset? preset)
    {
        if (!CanManage || preset == null) return;

        var dialog = new EditPresetDialog(preset);
        var result = await DialogHost.Show(dialog, "RootDialogHost");
        if (result is not PunishmentPreset updated) return;

        if (string.IsNullOrWhiteSpace(updated.Name))     { ErrorMessage = "Name is required.";     return; }
        if (string.IsNullOrWhiteSpace(updated.Category)) { ErrorMessage = "Category is required."; return; }

        IsLoading    = true;
        ErrorMessage = "";
        try
        {
            await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationPresetsUpdate,
                new
                {
                    id              = updated.Id,
                    category        = updated.Category,
                    name            = updated.Name,
                    description     = updated.Description,
                    actionType      = updated.ActionType,
                    durationSeconds = updated.DurationSeconds,
                    severity        = updated.Severity,
                    stackable       = updated.Stackable,
                    bypassCap       = updated.BypassCap,
                    requiresIpBan   = updated.RequiresIpBan,
                    enabled         = updated.Enabled
                },
                App.AuthService.SessionToken);

            await LoadPresetsAsync();
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsLoading = false; }
    }
}
