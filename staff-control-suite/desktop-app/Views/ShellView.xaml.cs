using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class ShellView : UserControl
{
    private readonly ShellViewModel _viewModel;

    public ShellView()
    {
        InitializeComponent();
        _viewModel = new ShellViewModel();
        DataContext = _viewModel;

        NavListBox.SelectionChanged += (s, e) =>
        {
            if (NavListBox.SelectedItem is ListBoxItem item && item.Tag is string tag)
            {
                NavigateTo(tag);
            }
        };
    }

    public void NavigateTo(string viewName)
    {
        _viewModel.CurrentViewTitle = viewName switch
        {
            "Overview"      => "Overview",
            "Servers"       => "Servers",
            "Console"       => "Console",
            "Players"       => "Players",
            "UsersRoles"    => "Users & Roles",
            "Settings"      => "Settings",
            "AuditLogs"     => "Audit Logs",
            "PlayerDetails" => "Player Details",
            _               => viewName
        };

        UIElement? view = viewName switch
        {
            "Overview"   => new OverviewView(),
            "Servers"    => new ServersView(),
            "Console"    => new ConsoleView(),
            "Players"    => new PlayersView(),
            "UsersRoles" => new UsersRolesView(),
            "Settings"   => new SettingsView(),
            "AuditLogs"  => new AuditLogsView(),
            _            => new OverviewView()
        };

        ContentArea.Content = view;

        // Sync selection in sidebar (only for known top-level views)
        if (viewName != "PlayerDetails")
        {
            foreach (ListBoxItem item in NavListBox.Items)
            {
                if (item.Tag?.ToString() == viewName)
                {
                    NavListBox.SelectedItem = item;
                    break;
                }
            }
        }
    }

    public void ShowPlayerDetails(string uuid)
    {
        _viewModel.CurrentViewTitle = "Player Details";
        ContentArea.Content = new PlayerDetailsView(uuid);
    }
}
