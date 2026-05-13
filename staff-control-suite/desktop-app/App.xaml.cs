using System.Windows;
using StaffControlSuite.Services;

namespace StaffControlSuite;

public partial class App : Application
{
    public static WebSocketService WebSocketService { get; private set; } = null!;
    public static AuthService AuthService { get; private set; } = null!;
    public static NavigationService NavigationService { get; private set; } = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        AppSettings.Load();
        WebSocketService = new WebSocketService();
        AuthService = new AuthService();
        NavigationService = new NavigationService();

        var mainWindow = new MainWindow();
        NavigationService.Initialize(mainWindow);
        mainWindow.Show();
    }
}
