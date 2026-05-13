using System.Windows;
using System.Windows.Controls;
using MaterialDesignThemes.Wpf;
using StaffControlSuite.Models;

namespace StaffControlSuite.Views.Dialogs;

internal class PermissionCheckItem
{
    public string Node { get; set; } = "";
    public string DisplayName { get; set; } = "";
    public bool IsChecked { get; set; }
}

public partial class RoleEditDialog : UserControl
{
    private readonly List<PermissionCheckItem> _items;
    private readonly bool _isEdit;

    public RoleEditDialog(Role? existing, List<Permission> allPermissions)
    {
        InitializeComponent();
        _isEdit = existing != null;

        _items = allPermissions.Select(p => new PermissionCheckItem
        {
            Node = p.Node,
            DisplayName = p.DisplayName,
            IsChecked = existing?.Permissions.Contains(p.Node) ?? false
        }).ToList();

        PermissionsList.ItemsSource = _items;

        if (existing != null)
        {
            TitleText.Text = "Edit Role";
            NameBox.Text = existing.Name;
            NameBox.IsEnabled = false; // Can't change name on edit
            DisplayNameBox.Text = existing.DisplayName;
        }
        else
        {
            TitleText.Text = "Create Role";
        }
    }

    private void CancelClick(object sender, RoutedEventArgs e)
    {
        DialogHost.CloseDialogCommand.Execute(null, this);
    }

    private void SaveClick(object sender, RoutedEventArgs e)
    {
        ErrorText.Visibility = Visibility.Collapsed;

        if (!_isEdit && string.IsNullOrWhiteSpace(NameBox.Text))
        {
            ErrorText.Text = "Role name is required.";
            ErrorText.Visibility = Visibility.Visible;
            return;
        }
        if (string.IsNullOrWhiteSpace(DisplayNameBox.Text))
        {
            ErrorText.Text = "Display name is required.";
            ErrorText.Visibility = Visibility.Visible;
            return;
        }

        var role = new Role
        {
            Name = NameBox.Text.Trim().ToLower(),
            DisplayName = DisplayNameBox.Text.Trim(),
            Permissions = _items.Where(p => p.IsChecked).Select(p => p.Node).ToList()
        };

        DialogHost.CloseDialogCommand.Execute(role, this);
    }
}
