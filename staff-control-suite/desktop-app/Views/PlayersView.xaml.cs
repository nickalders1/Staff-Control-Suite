using System.Windows.Controls;
using System.Windows.Input;
using StaffControlSuite.Models;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class PlayersView : UserControl
{
    private readonly PlayersViewModel _viewModel;

    public PlayersView()
    {
        InitializeComponent();
        _viewModel = new PlayersViewModel();
        DataContext = _viewModel;
        Loaded += async (s, e) => await _viewModel.LoadPlayersAsync();
    }

    private void DataGrid_MouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (sender is DataGrid grid && grid.SelectedItem is PlayerInfo player)
        {
            // Navigate to player details by replacing this view's content in the shell
            // Find the ShellView's ContentArea and replace it
            var shellView = this.TryFindParent<ShellView>();
            if (shellView != null)
            {
                shellView.ShowPlayerDetails(player.Uuid);
            }
        }
    }
}

internal static class VisualTreeHelperExtensions
{
    public static T? TryFindParent<T>(this System.Windows.DependencyObject element) where T : System.Windows.DependencyObject
    {
        var parent = System.Windows.Media.VisualTreeHelper.GetParent(element);
        while (parent != null)
        {
            if (parent is T target) return target;
            parent = System.Windows.Media.VisualTreeHelper.GetParent(parent);
        }
        return null;
    }
}
