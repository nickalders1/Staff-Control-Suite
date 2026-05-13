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

    private string _searchText = "";
    public string SearchText
    {
        get => _searchText;
        set
        {
            if (SetProperty(ref _searchText, value))
            {
                CurrentPage = 1;
                _ = LoadAsync();
            }
        }
    }

    private string _filterUsername = "";
    public string FilterUsername
    {
        get => _filterUsername;
        set
        {
            if (SetProperty(ref _filterUsername, value))
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
        ActionFilters.Add("AUTH_LOGIN");
        ActionFilters.Add("AUTH_LOGOUT");
        ActionFilters.Add("AUTH_LOGIN_FAILED");
        ActionFilters.Add("CONSOLE_COMMAND");
        ActionFilters.Add("SERVER_ADD");
        ActionFilters.Add("SERVER_REMOVE");
        ActionFilters.Add("SERVER_UPDATE");
        ActionFilters.Add("USER_CREATE");
        ActionFilters.Add("USER_UPDATE");
        ActionFilters.Add("USER_DELETE");
        ActionFilters.Add("ROLE_CREATE");
        ActionFilters.Add("ROLE_UPDATE");
        ActionFilters.Add("ROLE_DELETE");
        ActionFilters.Add("SETUP_CREATE_OWNER");
        ActionFilters.Add("AGENT_CONNECT");
        ActionFilters.Add("AGENT_DISCONNECT");
        ActionFilters.Add("AGENT_TIMEOUT");

        _selectedAction = "All Actions";
    }

    [RelayCommand]
    public async Task LoadAsync()
    {
        IsLoading = true;
        try
        {
            var action   = SelectedAction == "All Actions" ? null : SelectedAction;
            var username = string.IsNullOrWhiteSpace(FilterUsername) ? null : FilterUsername.Trim();
            var search   = string.IsNullOrWhiteSpace(SearchText) ? null : SearchText.Trim();

            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.AuditList,
                new { page = CurrentPage, pageSize = PageSize, action, username, search },
                App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                Logs.Clear();

                JsonElement logsArr = default;
                if (result.ValueKind == JsonValueKind.Array)
                    logsArr = result;
                else if (result.TryGetProperty("logs", out var la))
                    logsArr = la;

                if (logsArr.ValueKind == JsonValueKind.Array)
                    foreach (var log in logsArr.EnumerateArray())
                        Logs.Add(ParseLog(log));

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
        catch { }
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

    private static AuditLog ParseLog(JsonElement log) => new()
    {
        Id        = log.TryGetProperty("id",        out var id)  ? id.GetInt64()        : 0,
        UserId    = log.TryGetProperty("userId",    out var uid) ? uid.GetInt64()       : 0,
        Username  = log.TryGetProperty("username",  out var un)  ? un.GetString()  ?? "" : "",
        Action    = log.TryGetProperty("action",    out var a)   ? a.GetString()   ?? "" : "",
        Target    = log.TryGetProperty("target",    out var t)   ? t.GetString()   ?? "" : "",
        Details   = log.TryGetProperty("details",   out var d)   ? d.GetString()   ?? "" : "",
        IpAddress = log.TryGetProperty("ipAddress", out var ip)  ? ip.GetString()  ?? "" : "",
        Timestamp = log.TryGetProperty("timestamp", out var ts)  ? ts.GetInt64()        : 0L,
    };
}
