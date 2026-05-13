using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class AuditLogsView : UserControl
{
    public AuditLogsView()
    {
        InitializeComponent();
        var vm = new AuditLogsViewModel();
        DataContext = vm;
        Loaded += async (s, e) => await vm.LoadAsync();
    }
}
