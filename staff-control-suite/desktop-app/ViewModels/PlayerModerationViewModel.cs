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

public partial class PlayerModerationViewModel : ObservableObject
{
    public ObservableCollection<Punishment>  History           { get; } = new();
    public ObservableCollection<Punishment>  ActivePunishments { get; } = new();
    public ObservableCollection<PlayerNote>  Notes             { get; } = new();
    public ObservableCollection<string>      ActionTypeFilters { get; } = new();
    public ObservableCollection<string>      StatusFilters     { get; } = new();

    [ObservableProperty] private string _searchQuery       = "";
    [ObservableProperty] private string _targetPlayerName  = "";
    [ObservableProperty] private bool   _isLoading;
    [ObservableProperty] private bool   _isPlayerFound;
    [ObservableProperty] private string _errorMessage      = "";
    [ObservableProperty] private string _newNoteText       = "";
    [ObservableProperty] private bool   _isAddingNote;

    private string _filterActionType = "All";
    public string FilterActionType
    {
        get => _filterActionType;
        set
        {
            if (SetProperty(ref _filterActionType, value))
                _ = LoadHistoryAsync();
        }
    }

    private string _filterStatus = "All";
    public string FilterStatus
    {
        get => _filterStatus;
        set
        {
            if (SetProperty(ref _filterStatus, value))
                _ = LoadHistoryAsync();
        }
    }

    // Permissions
    public bool CanViewModeration =>
        App.AuthService.HasPermission("moderation.view") || App.AuthService.IsOwner();
    public bool CanViewNotes =>
        App.AuthService.HasPermission("moderation.notes.view") || App.AuthService.IsOwner();
    public bool CanCreateNotes =>
        App.AuthService.HasPermission("moderation.notes.create") || App.AuthService.IsOwner();
    public bool CanPunish =>
        App.AuthService.HasPermission("moderation.warn")  ||
        App.AuthService.HasPermission("moderation.kick")  ||
        App.AuthService.HasPermission("moderation.mute")  ||
        App.AuthService.HasPermission("moderation.ban")   ||
        App.AuthService.IsOwner();
    public bool CanRevoke =>
        App.AuthService.HasPermission("moderation.revoke") || App.AuthService.IsOwner();

    public PlayerModerationViewModel(string playerName = "")
    {
        if (!string.IsNullOrWhiteSpace(playerName))
        {
            TargetPlayerName = playerName;
            SearchQuery      = playerName;
            IsPlayerFound    = true;
        }

        ActionTypeFilters.Add("All");
        ActionTypeFilters.Add("WARN");
        ActionTypeFilters.Add("KICK");
        ActionTypeFilters.Add("MUTE");
        ActionTypeFilters.Add("TEMP_MUTE");
        ActionTypeFilters.Add("BAN");
        ActionTypeFilters.Add("TEMP_BAN");
        ActionTypeFilters.Add("IP_BAN");
        ActionTypeFilters.Add("TEMP_IP_BAN");
        ActionTypeFilters.Add("UNBAN");
        ActionTypeFilters.Add("UNMUTE");

        StatusFilters.Add("All");
        StatusFilters.Add("Active");
        StatusFilters.Add("Expired");
        StatusFilters.Add("Revoked");

        App.WebSocketService.PunishmentCreated += OnPunishmentCreated;
        App.WebSocketService.PunishmentRevoked += OnPunishmentRevoked;
    }

    private void OnPunishmentCreated(Punishment p)
    {
        if (!string.Equals(p.TargetName, TargetPlayerName, StringComparison.OrdinalIgnoreCase)) return;
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            History.Insert(0, p);
            if (p.Active) ActivePunishments.Insert(0, p);
        });
    }

    private void OnPunishmentRevoked(long punishmentId)
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            var p = History.FirstOrDefault(x => x.Id == punishmentId);
            if (p != null) p.Active = false;
            var ap = ActivePunishments.FirstOrDefault(x => x.Id == punishmentId);
            if (ap != null) ActivePunishments.Remove(ap);
        });
    }

    [RelayCommand]
    public async Task SearchPlayerAsync()
    {
        if (string.IsNullOrWhiteSpace(SearchQuery)) return;
        TargetPlayerName = SearchQuery.Trim();
        IsPlayerFound    = true;
        ErrorMessage     = "";
        await LoadAllAsync();
    }

    private async Task LoadAllAsync()
    {
        await LoadHistoryAsync();
        await LoadActiveAsync();
        if (CanViewNotes) await LoadNotesAsync();
    }

    [RelayCommand]
    public async Task LoadHistoryAsync()
    {
        if (!CanViewModeration || string.IsNullOrWhiteSpace(TargetPlayerName)) return;
        IsLoading    = true;
        ErrorMessage = "";
        try
        {
            var action  = FilterActionType == "All" ? null : FilterActionType;
            var status  = FilterStatus     == "All" ? null : FilterStatus.ToLower();

            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationHistory,
                new { targetName = TargetPlayerName, actionType = action, status },
                App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                History.Clear();
                JsonElement arr = default;
                if (result.TryGetProperty("punishments", out var pa))  arr = pa;
                else if (result.TryGetProperty("history", out var ha)) arr = ha;
                else if (result.ValueKind == JsonValueKind.Array)      arr = result;

                if (arr.ValueKind == JsonValueKind.Array)
                    foreach (var item in arr.EnumerateArray())
                        History.Add(Punishment.FromJson(item));
            });
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsLoading = false; }
    }

    [RelayCommand]
    public async Task LoadActiveAsync()
    {
        if (!CanViewModeration || string.IsNullOrWhiteSpace(TargetPlayerName)) return;
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationActive,
                new { targetName = TargetPlayerName },
                App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                ActivePunishments.Clear();
                JsonElement arr = default;
                if (result.TryGetProperty("punishments", out var pa)) arr = pa;
                else if (result.ValueKind == JsonValueKind.Array)     arr = result;

                if (arr.ValueKind == JsonValueKind.Array)
                    foreach (var item in arr.EnumerateArray())
                        ActivePunishments.Add(Punishment.FromJson(item));
            });
        }
        catch { /* silent */ }
    }

    [RelayCommand]
    public async Task LoadNotesAsync()
    {
        if (!CanViewNotes || string.IsNullOrWhiteSpace(TargetPlayerName)) return;
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationNotesList,
                new { targetName = TargetPlayerName },
                App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                Notes.Clear();
                JsonElement arr = default;
                if (result.TryGetProperty("notes", out var na)) arr = na;
                else if (result.ValueKind == JsonValueKind.Array) arr = result;

                if (arr.ValueKind == JsonValueKind.Array)
                    foreach (var item in arr.EnumerateArray())
                        Notes.Add(PlayerNote.FromJson(item));
            });
        }
        catch { /* silent */ }
    }

    [RelayCommand]
    public async Task OpenPunishDialogAsync()
    {
        if (!CanPunish || string.IsNullOrWhiteSpace(TargetPlayerName)) return;
        var dialog = new CreatePunishmentDialog(TargetPlayerName);
        var result = await DialogHost.Show(dialog, "RootDialogHost");
        if (result is Punishment)
        {
            await LoadHistoryAsync();
            await LoadActiveAsync();
        }
    }

    [RelayCommand]
    public async Task RevokeAsync(long punishmentId)
    {
        if (!CanRevoke) return;
        var confirm = await DialogHost.Show(
            new ConfirmDialog("Revoke punishment?", "This will remove the active punishment from the player."),
            "RootDialogHost");
        if (confirm is not true) return;

        IsLoading    = true;
        ErrorMessage = "";
        try
        {
            await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationRevoke,
                new { punishmentId },
                App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                var ap = ActivePunishments.FirstOrDefault(p => p.Id == punishmentId);
                if (ap != null) ActivePunishments.Remove(ap);
                var hp = History.FirstOrDefault(p => p.Id == punishmentId);
                if (hp != null) hp.Active = false;
            });
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsLoading = false; }
    }

    [RelayCommand]
    public async Task AddNoteAsync()
    {
        if (!CanCreateNotes || string.IsNullOrWhiteSpace(NewNoteText) || string.IsNullOrWhiteSpace(TargetPlayerName))
            return;

        IsAddingNote = true;
        try
        {
            await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationNotesCreate,
                new { targetName = TargetPlayerName, note = NewNoteText.Trim() },
                App.AuthService.SessionToken);

            NewNoteText = "";
            await LoadNotesAsync();
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsAddingNote = false; }
    }
}
