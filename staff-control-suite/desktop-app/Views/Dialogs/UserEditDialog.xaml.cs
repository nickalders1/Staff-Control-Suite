using System.Windows;
using System.Windows.Controls;
using MaterialDesignThemes.Wpf;
using StaffControlSuite.Models;

namespace StaffControlSuite.Views.Dialogs;

public partial class UserEditDialog : UserControl
{
    private readonly bool _isEdit;

    public UserEditDialog(User? existing, List<string> roleNames)
    {
        InitializeComponent();
        _isEdit = existing != null;

        foreach (var role in roleNames)
            RoleCombo.Items.Add(role);

        if (existing != null)
        {
            TitleText.Text = "Edit User";
            UsernameBox.Text = existing.Username;
            RoleCombo.SelectedItem = existing.RoleName;
        }
        else
        {
            if (RoleCombo.Items.Count > 0)
                RoleCombo.SelectedIndex = 0;
        }
    }

    private void CancelClick(object sender, RoutedEventArgs e)
    {
        DialogHost.CloseDialogCommand.Execute(null, this);
    }

    private void SaveClick(object sender, RoutedEventArgs e)
    {
        ErrorText.Visibility = Visibility.Collapsed;

        if (string.IsNullOrWhiteSpace(UsernameBox.Text))
        {
            ErrorText.Text = "Username is required.";
            ErrorText.Visibility = Visibility.Visible;
            return;
        }
        if (!_isEdit && string.IsNullOrWhiteSpace(PasswordBox.Password))
        {
            ErrorText.Text = "Password is required for new users.";
            ErrorText.Visibility = Visibility.Visible;
            return;
        }
        if (RoleCombo.SelectedItem == null)
        {
            ErrorText.Text = "Please select a role.";
            ErrorText.Visibility = Visibility.Visible;
            return;
        }

        DialogHost.CloseDialogCommand.Execute(
            (UsernameBox.Text.Trim(), PasswordBox.Password, RoleCombo.SelectedItem?.ToString() ?? ""),
            this);
    }
}
