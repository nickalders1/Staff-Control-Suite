using System.Windows;
using System.Windows.Controls;
using MaterialDesignThemes.Wpf;
using StaffControlSuite.Models;

namespace StaffControlSuite.Views.Dialogs;

public partial class EditPresetDialog : UserControl
{
    private readonly PunishmentPreset _preset;

    public EditPresetDialog(PunishmentPreset preset)
    {
        InitializeComponent();
        _preset = preset;

        CategoryBox.Text        = preset.Category;
        NameBox.Text            = preset.Name;
        DescriptionBox.Text     = preset.Description;
        DurationBox.Text        = preset.DurationSeconds.ToString();
        SeveritySlider.Value    = preset.Severity;
        StackableCheck.IsChecked    = preset.Stackable;
        BypassCapCheck.IsChecked    = preset.BypassCap;
        RequiresIpBanCheck.IsChecked = preset.RequiresIpBan;
        EnabledCheck.IsChecked  = preset.Enabled;

        // Select matching action type in the ComboBox
        foreach (ComboBoxItem item in ActionTypeBox.Items)
            if (item.Content?.ToString() == preset.ActionType)
            {
                ActionTypeBox.SelectedItem = item;
                break;
            }
    }

    private void SaveButton_Click(object sender, RoutedEventArgs e)
    {
        var actionType = (ActionTypeBox.SelectedItem as ComboBoxItem)?.Content?.ToString()
                         ?? _preset.ActionType;

        long.TryParse(DurationBox.Text, out var durationSecs);

        var updated = new PunishmentPreset
        {
            Id              = _preset.Id,
            Category        = CategoryBox.Text.Trim(),
            Name            = NameBox.Text.Trim(),
            Description     = DescriptionBox.Text.Trim(),
            ActionType      = actionType,
            DurationSeconds = durationSecs,
            Severity        = (int)SeveritySlider.Value,
            Stackable       = StackableCheck.IsChecked == true,
            BypassCap       = BypassCapCheck.IsChecked == true,
            RequiresIpBan   = RequiresIpBanCheck.IsChecked == true,
            Enabled         = EnabledCheck.IsChecked == true,
        };

        DialogHost.CloseDialogCommand.Execute(updated, this);
    }

    private void CancelButton_Click(object sender, RoutedEventArgs e)
        => DialogHost.CloseDialogCommand.Execute(null, this);
}
