using System.Windows;
using System.Windows.Controls;
using MaterialDesignThemes.Wpf;

namespace StaffControlSuite.Views.Dialogs;

public partial class ConfirmDialog : UserControl
{
    public ConfirmDialog(string title, string body)
    {
        InitializeComponent();
        TitleText.Text = title;
        BodyText.Text = body;
    }

    private void CancelClick(object sender, RoutedEventArgs e)
    {
        DialogHost.CloseDialogCommand.Execute(false, this);
    }

    private void ConfirmClick(object sender, RoutedEventArgs e)
    {
        DialogHost.CloseDialogCommand.Execute(true, this);
    }
}
