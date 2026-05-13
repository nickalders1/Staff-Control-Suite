using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class PresetManagerView : UserControl
{
    private readonly PresetManagerViewModel _viewModel;

    public PresetManagerView()
    {
        InitializeComponent();
        _viewModel  = new PresetManagerViewModel();
        DataContext = _viewModel;
        Loaded     += async (_, _) => await _viewModel.LoadPresetsAsync();
    }
}
