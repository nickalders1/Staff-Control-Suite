using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using StaffControlSuite.Protocol;

namespace StaffControlSuite;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        Loaded += OnLoaded;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        await InitializeAsync();
    }

    private async Task InitializeAsync()
    {
        try
        {
            var host = Services.AppSettings.Current.ProxyHost;
            var port = Services.AppSettings.Current.ProxyPort;

            await App.WebSocketService.ConnectAsync(host, port);

            var result = await App.WebSocketService.SendRequestAsync(MessageTypes.SetupCheck);

            bool setupNeeded = false;
            if (result.ValueKind != JsonValueKind.Undefined && result.ValueKind != JsonValueKind.Null)
            {
                if (result.TryGetProperty("needsSetup", out var setupProp))
                    setupNeeded = setupProp.GetBoolean();
            }

            if (setupNeeded)
                App.NavigationService.ShowFirstSetup();
            else
                App.NavigationService.ShowLogin();
        }
        catch
        {
            // If we can't connect, show login — let the user specify host/port
            App.NavigationService.ShowLogin();
        }
    }

    public void SetContent(UIElement content)
    {
        MainContent.Content = content;
    }
}
