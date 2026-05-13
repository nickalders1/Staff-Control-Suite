using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class FirstSetupView : UserControl
{
    private readonly FirstSetupViewModel _viewModel;

    public FirstSetupView()
    {
        InitializeComponent();
        _viewModel = new FirstSetupViewModel();
        DataContext = _viewModel;

        PasswordBox.PasswordChanged += (s, e) =>
        {
            _viewModel.Password = PasswordBox.Password;
        };

        ConfirmPasswordBox.PasswordChanged += (s, e) =>
        {
            _viewModel.ConfirmPassword = ConfirmPasswordBox.Password;
        };
    }
}
