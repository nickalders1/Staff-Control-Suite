using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;

namespace StaffControlSuite.ViewModels;

public partial class AuditLogsViewModel : ObservableObject
{
    private const int PageSize = 50;

    public ObservableCollection<AuditLog> Logs { get; } = new();
    public ObservableCollection<string> ActionFilters { get; } = new();

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanGoPrevious))]
    [NotifyPropertyChangedFor(nameof(CanGoNext))]
    private int _currentPage = 1;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanGoNext))]
    private int _totalPages = 1;

    [ObservableProperty] private int _total;
    [ObservableProperty] private bool _isLoading;

    private string? _selectedAction;
    public string? SelectedAction
    {
        get => _selectedAction;
        set
        {
            if (SetProperty(ref _selectedAction, value))
            {
                CurrentPage = 1;
                _ = LoadAsync();
            }
        }
    }

    public bool CanGoPrevious => CurrentPage > 1;
    public bool CanGoNext => CurrentPage < TotalPages;

    public AuditLogsViewModel()
    {
        ActionFilters.Add("All Actions");
        ActionFilters.Add("auth.login");
        ActionFilters.Add("auth.logout");
        ActionFilters.Add("users.create");
        ActionFilters.Add("users.update");
        ActionFilters.Add("users.delete");
        ActionFilters.Add("roles.create");
        ActionFilters.Add("roles.update");
        ActionFilters.Add("roles.delete");
        ActionFilters.Add("servers.add");
        ActionFilters.Add("servers.remove");
        ActionFilters.Add("console.command");

        _selectedAction = "All Actions";
    }

    [RelayCommand]
    public async Task LoadAsync()
    {
        IsLoading = true;
        try
        {
            var action = SelectedAction == "All Actions" ? null : SelectedAction;
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.AuditList,
                new { page = CurrentPage, pageSize = PageSize, action },
                App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                Logs.Clear();

                // Try to get logs array from various response shapes
                JsonElement logsArr = default;
                if (result.ValueKind == JsonValueKind.Array)
                {
                    logsArr = result;
                }
                else if (result.TryGetProperty("logs", out var la))
                {
                    logsArr = la;
                }

                if (logsArr.ValueKind == JsonValueKind.Array)
                {
                    foreach (var log in logsArr.EnumerateArray())
                        Logs.Add(ParseLog(log));
                }

                if (result.TryGetProperty("total", out var tot))
                    Total = tot.GetInt32();
                else
                    Total = Logs.Count;

                if (result.TryGetProperty("totalPages", out var tp))
                    TotalPages = tp.GetInt32();
                else
                    TotalPages = Math.Max(1, (int)Math.Ceiling((double)Total / PageSize));
            });
        }
        catch
        {
            // Silently handle
        }
        finally
        {
            IsLoading = false;
        }
    }

    [RelayCommand]
    private async Task NextPageAsync()
    {
        if (CurrentPage < TotalPages)
        {
            CurrentPage++;
            await LoadAsync();
        }
    }

    [RelayCommand]
    private async Task PreviousPageAsync()
    {
        if (CurrentPage > 1)
        {
            CurrentPage--;
            await LoadAsync();
        }
    }

    private static AuditLog ParseLog(JsonElement log)
    {
        return new AuditLog
        {
            Id = log.TryGetProperty("id", out var id) ? id.GetInt64() : 0,
            UserId = log.TryGetProperty("userId", out var uid) ? uid.GetInt64() : 0,
            Username = log.TryGetProperty("username", out var un) ? un.GetString() ?? "" : "",
            Action = log.TryGetProperty("action", out var a) ? a.GetString() ?? "" : "",
            Target = log.TryGetProperty("target", out var t) ? t.GetString() ?? "" : "",
            Details = log.TryGetProperty("details", out var d) ? d.GetString() ?? "" : "",
            IpAddress = log.TryGetProperty("ipAddress", out var ip) ? ip.GetString() ?? "" : "",
            Timestamp = log.TryGetProperty("timestamp", out var ts) ? ts.GetInt64() : 0L,
        };
    }
}
