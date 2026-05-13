using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class OverviewView : UserControl
{
    public OverviewView()
    {
        InitializeComponent();
        var vm = new OverviewViewModel();
        DataContext = vm;
        Loaded += async (s, e) => await vm.LoadAsync();
    }
}
