using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Protocol;
using StaffControlSuite.Services;

namespace StaffControlSuite.ViewModels;

public partial class SettingsViewModel : ObservableObject
{
    [ObservableProperty] private string _proxyHost = AppSettings.Current.ProxyHost;
    [ObservableProperty] private int _proxyPort = AppSettings.Current.ProxyPort;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasSaveMessage))]
    private string _saveMessage = "";

    public bool HasSaveMessage => !string.IsNullOrEmpty(SaveMessage);

    public string Username => App.AuthService.CurrentUser?.Username ?? "Unknown";
    public string UserRole => App.AuthService.CurrentUser?.RoleName ?? "Unknown";
    public int PermissionCount => App.AuthService.CurrentUser?.Permissions.Count ?? 0;

    public string MaskedToken
    {
        get
        {
            var token = App.AuthService.SessionToken;
            if (string.IsNullOrEmpty(token) || token.Length < 8)
                return "••••••••";
            return token[..4] + "••••••••" + token[^4..];
        }
    }

    [RelayCommand]
    private void SaveConnection()
    {
        AppSettings.Current.ProxyHost = ProxyHost;
        AppSettings.Current.ProxyPort = ProxyPort;
        AppSettings.Save();
        SaveMessage = "Settings saved successfully.";

        // Clear the message after 3 seconds
        _ = Task.Delay(3000).ContinueWith(_ =>
        {
            System.Windows.Application.Current.Dispatcher.Invoke(() =>
            {
                SaveMessage = "";
            });
        });
    }

    [RelayCommand]
    private async Task LogoutAsync()
    {
        try
        {
            if (App.WebSocketService.IsConnected && App.AuthService.SessionToken != null)
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.AuthLogout,
                    null,
                    App.AuthService.SessionToken);
            }
        }
        catch { /* ignore */ }
        finally
        {
            App.AuthService.ClearSession();
            await App.WebSocketService.DisconnectAsync();
            App.NavigationService.ShowLogin();
        }
    }
}
