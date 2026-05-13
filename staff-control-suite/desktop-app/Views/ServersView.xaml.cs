using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class ServersView : UserControl
{
    private ServersViewModel _vm = null!;

    public ServersView()
    {
        InitializeComponent();
        _vm = new ServersViewModel();
        DataContext = _vm;
        Loaded += async (s, e) => await _vm.LoadServersAsync();
    }

    private async void MainTabControl_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (sender is not TabControl tc) return;
        if (tc.SelectedIndex == 1 && _vm.AgentStatuses.Count == 0)
            await _vm.LoadAgentStatusAsync();
    }
}
