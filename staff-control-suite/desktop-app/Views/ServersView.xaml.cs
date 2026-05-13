using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class ServersView : UserControl
{
    public ServersView()
    {
        InitializeComponent();
        var vm = new ServersViewModel();
        DataContext = vm;
        Loaded += async (s, e) => await vm.LoadServersAsync();
    }
}
