using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class PlayerModerationView : UserControl
{
    private readonly PlayerModerationViewModel _viewModel;

    public PlayerModerationView(string playerName = "")
    {
        InitializeComponent();
        _viewModel  = new PlayerModerationViewModel(playerName);
        DataContext = _viewModel;

        if (!string.IsNullOrWhiteSpace(playerName))
        {
            Loaded += async (_, _) =>
            {
                await _viewModel.LoadHistoryAsync();
                await _viewModel.LoadActiveAsync();
                if (_viewModel.CanViewNotes) await _viewModel.LoadNotesAsync();
            };
        }
    }
}
