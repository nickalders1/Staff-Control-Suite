using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views.Dialogs;

public partial class CreatePunishmentDialog : UserControl
{
    private readonly CreatePunishmentViewModel _viewModel;

    public CreatePunishmentDialog(string targetName)
    {
        InitializeComponent();
        _viewModel  = new CreatePunishmentViewModel(targetName);
        DataContext = _viewModel;
        Loaded     += async (_, _) => await _viewModel.LoadPresetsAsync();
    }
}
