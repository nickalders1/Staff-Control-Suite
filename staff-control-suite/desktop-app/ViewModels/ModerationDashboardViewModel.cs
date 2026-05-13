using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;

namespace StaffControlSuite.ViewModels;

public partial class ModerationDashboardViewModel : ObservableObject
{
    public ObservableCollection<Punishment> RecentPunishments { get; } = new();

    [ObservableProperty] private int    _activeBans;
    [ObservableProperty] private int    _activeMutes;
    [ObservableProperty] private int    _punishmentsToday;
    [ObservableProperty] private bool   _isLoading;
    [ObservableProperty] private string _errorMessage = "";

    public bool CanView => App.AuthService.HasPermission("moderation.view") || App.AuthService.IsOwner();

    public ModerationDashboardViewModel()
    {
        App.WebSocketService.PunishmentCreated += OnPunishmentCreated;
        App.WebSocketService.PunishmentRevoked += OnPunishmentRevoked;
    }

    private void OnPunishmentCreated(Punishment p)
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            RecentPunishments.Insert(0, p);
            if (RecentPunishments.Count > 50)
                RecentPunishments.RemoveAt(RecentPunishments.Count - 1);

            if (p.Active)
            {
                if (p.ActionType is "BAN" or "TEMP_BAN" or "IP_BAN" or "TEMP_IP_BAN")
                    ActiveBans++;
                else if (p.ActionType is "MUTE" or "TEMP_MUTE")
                    ActiveMutes++;
            }
            PunishmentsToday++;
        });
    }

    private void OnPunishmentRevoked(long punishmentId)
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            var p = RecentPunishments.FirstOrDefault(x => x.Id == punishmentId);
            if (p != null)
            {
                p.Active = false;
                if (p.ActionType is "BAN" or "TEMP_BAN" or "IP_BAN" or "TEMP_IP_BAN")
                    ActiveBans = Math.Max(0, ActiveBans - 1);
                else if (p.ActionType is "MUTE" or "TEMP_MUTE")
                    ActiveMutes = Math.Max(0, ActiveMutes - 1);
            }
        });
    }

    [RelayCommand]
    public async Task LoadAsync()
    {
        if (!CanView) return;

        IsLoading    = true;
        ErrorMessage = "";
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ModerationDashboard, null, App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                RecentPunishments.Clear();

                if (result.TryGetProperty("activeBans",        out var ab))  ActiveBans        = ab.GetInt32();
                if (result.TryGetProperty("activeMutes",       out var am))  ActiveMutes       = am.GetInt32();
                if (result.TryGetProperty("punishmentsToday",  out var pt))  PunishmentsToday  = pt.GetInt32();

                JsonElement arr = default;
                if (result.TryGetProperty("recentPunishments", out var rp))  arr = rp;
                else if (result.ValueKind == JsonValueKind.Array)             arr = result;

                if (arr.ValueKind == JsonValueKind.Array)
                    foreach (var item in arr.EnumerateArray())
                        RecentPunishments.Add(Punishment.FromJson(item));
            });
        }
        catch (Exception ex) { ErrorMessage = ex.Message; }
        finally { IsLoading = false; }
    }
}
