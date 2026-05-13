using System.Windows.Controls;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class LoginView : UserControl
{
    private readonly LoginViewModel _viewModel;

    public LoginView()
    {
        InitializeComponent();
        _viewModel = new LoginViewModel();
        DataContext = _viewModel;

        PasswordBox.PasswordChanged += (s, e) =>
        {
            _viewModel.Password = PasswordBox.Password;
        };
    }
}
