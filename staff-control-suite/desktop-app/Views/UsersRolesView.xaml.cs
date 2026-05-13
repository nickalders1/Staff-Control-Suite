using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class UsersRolesView : UserControl
{
    public UsersRolesView()
    {
        InitializeComponent();
        var vm = new UsersRolesViewModel();
        DataContext = vm;
        Loaded += async (s, e) =>
        {
            await vm.LoadUsersAsync();
            await vm.LoadRolesAsync();
        };
    }
}
