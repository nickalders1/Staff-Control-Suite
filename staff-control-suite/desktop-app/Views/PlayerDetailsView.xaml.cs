using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class PlayerDetailsView : UserControl
{
    public PlayerDetailsView(string uuid)
    {
        InitializeComponent();
        var vm = new PlayerDetailsViewModel(uuid);
        DataContext = vm;
        Loaded += async (s, e) => await vm.LoadPlayerAsync();
    }
}
