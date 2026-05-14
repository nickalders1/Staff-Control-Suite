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

public partial class PlayerDetailsViewModel : ObservableObject
{
    private readonly string _uuid;

    [ObservableProperty] private PlayerInfo? _player;
    [ObservableProperty] private bool   _isLoading;
    [ObservableProperty] private bool   _isLoadingPunishments;
    [ObservableProperty] private bool   _isLoadingNotes;
    [ObservableProperty] private bool   _isAddingNote;
    [ObservableProperty] private string _newNoteText  = "";
    [ObservableProperty] private string _errorMessage = "";

    public ObservableCollection<Punishment> ActivePunishments  { get; } = new();
    public ObservableCollection<Punishment> PunishmentHistory  { get; } = new();
    public ObservableCollection<PlayerNote> Notes              { get; } = new();

    public string FirstJoinedDisplay => Player?.FirstJoined > 0
        ? DateTimeOffset.FromUnixTimeMilliseconds(Player.FirstJoined).LocalDateTime.ToString("yyyy-MM-dd HH:mm")
        : "Unknown";

    public bool CanPunish =>
        App.AuthService.HasPermission("moderation.warn") ||
        App.AuthService.HasPermission("moderation.kick") ||
        App.AuthService.HasPermission("moderation.mute") ||
        App.AuthService.HasPermission("moderation.ban")  ||
        App.AuthService.IsOwner();

    public bool CanRevoke =>
        App.AuthService.HasPermission("moderation.revoke") || App.AuthService.IsOwner();

    public bool CanViewNotes =>
        App.AuthService.HasPermission("moderation.notes.view") || App.AuthService.IsOwner();

    public bool CanCreateNotes =>
        App.AuthService.HasPermission("moderation.notes.create") || App.AuthService.IsOwner();

    public bool CanViewModeration =>
        App.AuthService.HasPermission("moderation.view") || App.AuthService.IsOwner();

    public PlayerDetailsViewModel(string uuid)
    {
        _uuid = uuid;
        Player = new PlayerInfo { Uuid = uuid, Name = "Loading..." };

        App.WebSocketService.PunishmentCreated += OnPunishmentCreated;
        App.WebSocketService.PunishmentRevoked += OnPunishmentRevoked;
    }

    [RelayCommand]
    public async Task LoadPlayerAsync()
    {
        IsLoading = true;
        ErrorMessage = "";
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.PlayersDetails,
                new { uuid = _uuid },
                App.AuthService.SessionToken);

            var el = result.ValueKind == JsonValueKind.Object &&
                     result.TryGetProperty("player", out var pe) ? pe : result;

            Player = new PlayerInfo
            {
                Uuid            = el.TryGetProperty("uuid",            out var u)   ? u.GetString()   ?? "" : "",
                Name            = el.TryGetProperty("name",            out var n)   ? n.GetString()   ?? "" : "",
                ServerId        = el.TryGetProperty("serverId",        out var sid) ? sid.GetString() ?? "" : "",
                World           = el.TryGetProperty("world",           out var w)   ? w.GetString()   ?? "" : "",
                Gamemode        = el.TryGetProperty("gamemode",        out var gm)  ? gm.GetString()  ?? "" : "",
                Health          = el.TryGetProperty("health",          out var h)   ? h.GetDouble()        : 20.0,
                FoodLevel       = el.TryGetProperty("foodLevel",       out var fl)  ? fl.GetInt32()        : 20,
                Ping            = el.TryGetProperty("ping",            out var pg)  ? pg.GetInt32()        : 0,
                IsOnline        = el.TryGetProperty("isOnline",        out var io)  && io.GetBoolean(),
                FirstJoined     = el.TryGetProperty("firstJoined",     out var fj)  ? fj.GetInt64()        : 0L,
                LastJoined      = el.TryGetProperty("lastJoined",      out var lj)  ? lj.GetInt64()        : 0L,
                PlaytimeSeconds = el.TryGetProperty("playtimeSeconds", out var pt)  ? pt.GetInt64()        : 0L,
            };

            OnPropertyChanged(nameof(FirstJoinedDisplay));

            // Load moderation data now that we have the player name
            if (CanViewModeration) await LoadPunishmentsAsync();
            if (CanViewNotes)      await LoadNotesAsync();
        }
        catch { }
        finally { IsLoading = false; }
    }

    private async Task LoadPunishmentsAsync()
    {
        if (string.IsNullOrWhiteSpace(Player?.Name)) return;
        IsLoadingPunishments = true;
        try
        {
            var histResult = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationHistory,
                new { targetName = Player.Name },
                App.AuthService.SessionToken);

            var actResult = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationActive,
                new { targetName = Player.Name },
                App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                PunishmentHistory.Clear();
                ActivePunishments.Clear();

                if (histResult.TryGetProperty("punishments", out var ha) ||
                    histResult.TryGetProperty("history",     out ha))
                    if (ha.ValueKind == JsonValueKind.Array)
                        foreach (var item in ha.EnumerateArray())
                            PunishmentHistory.Add(Punishment.FromJson(item));

                if (actResult.TryGetProperty("punishments", out var aa))
                    if (aa.ValueKind == JsonValueKind.Array)
                        foreach (var item in aa.EnumerateArray())
                            ActivePunishments.Add(Punishment.FromJson(item));
            });
        }
        catch { }
        finally { IsLoadingPunishments = false; }
    }

    private async Task LoadNotesAsync()
    {
        if (string.IsNullOrWhiteSpace(Player?.Name)) return;
        IsLoadingNotes = true;
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationNotesList,
                new { targetName = Player.Name },
                App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                Notes.Clear();
                if (result.TryGetProperty("notes", out var na) && na.ValueKind == JsonValueKind.Array)
                    foreach (var item in na.EnumerateArray())
                        Notes.Add(PlayerNote.FromJson(item));
            });
        }
        catch { }
        finally { IsLoadingNotes = false; }
    }

    [RelayCommand]
    public async Task RevokeAsync(long punishmentId)
    {
        if (!CanRevoke) return;
        var confirm = await DialogHost.Show(
            new ConfirmDialog("Revoke punishment?", "This will remove the active punishment from the player."),
            "RootDialogHost");
        if (confirm is not true) return;

        IsLoading = true;
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
                var hp = PunishmentHistory.FirstOrDefault(p => p.Id == punishmentId);
                if (hp != null) hp.Active = false;
            });
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsLoading = false; }
    }

    [RelayCommand]
    public async Task PunishPlayerAsync()
    {
        if (!CanPunish || string.IsNullOrWhiteSpace(Player?.Name)) return;
        var dialog = new CreatePunishmentDialog(Player.Name);
        var result = await DialogHost.Show(dialog, "RootDialogHost");
        if (result is Punishment)
            await LoadPunishmentsAsync();
    }

    [RelayCommand]
    public async Task AddNoteAsync()
    {
        if (!CanCreateNotes || string.IsNullOrWhiteSpace(NewNoteText) ||
            string.IsNullOrWhiteSpace(Player?.Name)) return;

        IsAddingNote = true;
        try
        {
            await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationNotesCreate,
                new { targetName = Player.Name, note = NewNoteText.Trim() },
                App.AuthService.SessionToken);
            NewNoteText = "";
            await LoadNotesAsync();
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsAddingNote = false; }
    }

    private void OnPunishmentCreated(Punishment p)
    {
        if (!string.Equals(p.TargetName, Player?.Name, StringComparison.OrdinalIgnoreCase)) return;
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            PunishmentHistory.Insert(0, p);
            if (p.Active) ActivePunishments.Insert(0, p);
        });
    }

    private void OnPunishmentRevoked(long punishmentId)
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            var ap = ActivePunishments.FirstOrDefault(x => x.Id == punishmentId);
            if (ap != null) ActivePunishments.Remove(ap);
            var hp = PunishmentHistory.FirstOrDefault(x => x.Id == punishmentId);
            if (hp != null) hp.Active = false;
        });
    }

    [RelayCommand]
    private void Back() => App.NavigationService.NavigateShell("Players");
}
