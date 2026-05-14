using System.IO;
using System.Text.Json;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;
using StaffControlSuite.Services;

namespace StaffControlSuite.ViewModels;

public partial class LoginViewModel : ObservableObject
{
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasError))]
    private string _errorMessage = "";

    [ObservableProperty] private string _proxyHost = AppSettings.Current.ProxyHost;
    [ObservableProperty] private int _proxyPort = AppSettings.Current.ProxyPort;
    [ObservableProperty] private string _username = "";
    [ObservableProperty] private string _password = "";
    [ObservableProperty] private bool _isLoading = false;

    public string LogoImagePath => AppSettings.Current.LogoImagePath ?? "";
    public bool HasLogo => !string.IsNullOrEmpty(AppSettings.Current.LogoImagePath) &&
                           File.Exists(AppSettings.Current.LogoImagePath);

    public bool HasError => !string.IsNullOrEmpty(ErrorMessage);

    [RelayCommand]
    private async Task LoginAsync()
    {
        if (string.IsNullOrWhiteSpace(Username))
        {
            ErrorMessage = "Username is required.";
            return;
        }
        if (string.IsNullOrWhiteSpace(Password))
        {
            ErrorMessage = "Password is required.";
            return;
        }

        IsLoading = true;
        ErrorMessage = "";

        try
        {
            AppSettings.Current.ProxyHost = ProxyHost;
            AppSettings.Current.ProxyPort = ProxyPort;
            AppSettings.Save();

            if (!App.WebSocketService.IsConnected)
                await App.WebSocketService.ConnectAsync(ProxyHost, ProxyPort);

            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.AuthLogin,
                new { username = Username, password = Password }
            );

            var token = result.TryGetProperty("token", out var tokenProp)
                ? tokenProp.GetString() ?? throw new Exception("No token received.")
                : throw new Exception("Invalid login response.");

            var user = ParseUser(result);
            App.AuthService.SetSession(token, user);
            App.NavigationService.ShowShell();
        }
        catch (Exception ex)
        {
            ErrorMessage = ex.Message;
        }
        finally
        {
            IsLoading = false;
        }
    }

    private static User ParseUser(JsonElement payload)
    {
        if (!payload.TryGetProperty("user", out var userEl))
            throw new Exception("No user object in response.");

        var user = new User
        {
            Id = userEl.TryGetProperty("id", out var id) ? id.GetInt64() : 0,
            Username = userEl.TryGetProperty("username", out var un) ? un.GetString() ?? "" : "",
            RoleId = userEl.TryGetProperty("roleId", out var rid) ? rid.GetInt64() : 0,
            RoleName = userEl.TryGetProperty("roleName", out var rn) ? rn.GetString() ?? "" : "",
            IsActive = userEl.TryGetProperty("isActive", out var ia) && ia.GetBoolean()
        };

        if (userEl.TryGetProperty("permissions", out var permsEl))
        {
            var permissions = new List<string>();
            foreach (var p in permsEl.EnumerateArray())
            {
                var pStr = p.GetString();
                if (pStr != null) permissions.Add(pStr);
            }
            user.Permissions = permissions;
        }

        return user;
    }
}
