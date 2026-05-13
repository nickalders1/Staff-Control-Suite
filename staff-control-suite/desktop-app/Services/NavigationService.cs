using System.Windows;
using StaffControlSuite.Views;

namespace StaffControlSuite.Services;

public class NavigationService
{
    private MainWindow? _mainWindow;
    private ShellView? _shellView;

    public void Initialize(MainWindow window)
    {
        _mainWindow = window;
    }

    public void NavigateTo(string viewName, object? parameter = null)
    {
        switch (viewName)
        {
            case "Login":
                ShowLogin();
                break;
            case "FirstSetup":
                ShowFirstSetup();
                break;
            default:
                ShowShell(viewName);
                break;
        }
    }

    public void ShowLogin()
    {
        Application.Current.Dispatcher.Invoke(() =>
        {
            _shellView = null;
            var loginView = new LoginView();
            _mainWindow!.SetContent(loginView);
        });
    }

    public void ShowFirstSetup()
    {
        Application.Current.Dispatcher.Invoke(() =>
        {
            _shellView = null;
            var setupView = new FirstSetupView();
            _mainWindow!.SetContent(setupView);
        });
    }

    public void ShowShell(string initialView = "Overview")
    {
        Application.Current.Dispatcher.Invoke(() =>
        {
            if (_shellView == null)
            {
                _shellView = new ShellView();
                _mainWindow!.SetContent(_shellView);
            }
            _shellView.NavigateTo(initialView);
        });
    }

    public void NavigateShell(string viewName)
    {
        Application.Current.Dispatcher.Invoke(() =>
        {
            _shellView?.NavigateTo(viewName);
        });
    }
}
