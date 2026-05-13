using System.Windows;
using System.Windows.Controls;
using MaterialDesignThemes.Wpf;
using StaffControlSuite.Models;

namespace StaffControlSuite.Views.Dialogs;

public partial class ServerEditDialog : UserControl
{
    private readonly bool _isEdit;

    public ServerEditDialog(ServerInfo? existing = null)
    {
        InitializeComponent();
        _isEdit = existing != null;

        if (existing != null)
        {
            TitleText.Text = "Edit Server";
            ServerIdBox.Text = existing.ServerId;
            ServerIdBox.IsEnabled = false; // Can't change ID on edit
            ServerNameBox.Text = existing.ServerName;
            HostBox.Text = existing.Host;
            PortBox.Text = existing.Port.ToString();

            foreach (ComboBoxItem item in ServerTypeBox.Items)
            {
                if (item.Content?.ToString() == existing.ServerType)
                {
                    ServerTypeBox.SelectedItem = item;
                    break;
                }
            }
        }
        else
        {
            ServerTypeBox.SelectedIndex = 0;
        }
    }

    private void CancelClick(object sender, RoutedEventArgs e)
    {
        DialogHost.CloseDialogCommand.Execute(null, this);
    }

    private void SaveClick(object sender, RoutedEventArgs e)
    {
        ErrorText.Visibility = Visibility.Collapsed;

        if (string.IsNullOrWhiteSpace(ServerIdBox.Text))
        {
            ErrorText.Text = "Server ID is required.";
            ErrorText.Visibility = Visibility.Visible;
            return;
        }
        if (string.IsNullOrWhiteSpace(ServerNameBox.Text))
        {
            ErrorText.Text = "Server name is required.";
            ErrorText.Visibility = Visibility.Visible;
            return;
        }
        if (!int.TryParse(PortBox.Text, out var port) || port < 1 || port > 65535)
        {
            ErrorText.Text = "Port must be a valid number (1-65535).";
            ErrorText.Visibility = Visibility.Visible;
            return;
        }

        var server = new ServerInfo
        {
            ServerId = ServerIdBox.Text.Trim(),
            ServerName = ServerNameBox.Text.Trim(),
            ServerType = (ServerTypeBox.SelectedItem as ComboBoxItem)?.Content?.ToString() ?? "PAPER",
            Host = string.IsNullOrWhiteSpace(HostBox.Text) ? "localhost" : HostBox.Text.Trim(),
            Port = port
        };

        DialogHost.CloseDialogCommand.Execute(server, this);
    }
}
