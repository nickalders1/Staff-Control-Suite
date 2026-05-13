using System.Windows;
using System.Windows.Threading;
using StaffControlSuite.Services;

namespace StaffControlSuite;

public partial class App : Application
{
    public static WebSocketService WebSocketService { get; private set; } = null!;
    public static AuthService AuthService { get; private set; } = null!;
    public static NavigationService NavigationService { get; private set; } = null!;
    public static ConsoleLogService ConsoleLogService { get; private set; } = null!;
    public static PlayerCacheService PlayerCacheService { get; private set; } = null!;
    public static BrandingService BrandingService { get; private set; } = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += OnDomainUnhandledException;

        AppSettings.Load();
        AppThemeService.Apply(
            AppSettings.Current.ThemeMode,
            AppSettings.Current.AccentColor,
            string.IsNullOrWhiteSpace(AppSettings.Current.SecondaryAccentColor)
                ? null : AppSettings.Current.SecondaryAccentColor);
        WebSocketService = new WebSocketService();
        AuthService = new AuthService();
        NavigationService = new NavigationService();
        ConsoleLogService = new ConsoleLogService(WebSocketService);
        PlayerCacheService = new PlayerCacheService(WebSocketService);
        BrandingService = new BrandingService();

        var mainWindow = new MainWindow();
        NavigationService.Initialize(mainWindow);
        mainWindow.Show();
    }

    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        ShowCrashDialog(e.Exception);
        e.Handled = true;
    }

    private void OnDomainUnhandledException(object sender, UnhandledExceptionEventArgs e)
    {
        if (e.ExceptionObject is Exception ex)
            ShowCrashDialog(ex);
    }

    private static void ShowCrashDialog(Exception ex)
    {
        var message = $"An unexpected error occurred:\n\n{ex.GetType().Name}: {ex.Message}";
        if (ex.InnerException != null)
            message += $"\n\nCaused by: {ex.InnerException.GetType().Name}: {ex.InnerException.Message}";
        message += $"\n\n--- Stack Trace ---\n{ex.StackTrace}";

        MessageBox.Show(message, "Staff Control — Unexpected Error",
            MessageBoxButton.OK, MessageBoxImage.Error);
    }
}
