using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class ModerationDashboardView : UserControl
{
    private readonly ModerationDashboardViewModel _viewModel;

    public ModerationDashboardView()
    {
        InitializeComponent();
        _viewModel  = new ModerationDashboardViewModel();
        DataContext = _viewModel;
        Loaded     += async (_, _) => await _viewModel.LoadAsync();
    }

    private void OpenPresetManager(object sender, System.Windows.RoutedEventArgs e)
    {
        App.NavigationService.NavigateShell("PresetManager");
    }
}
