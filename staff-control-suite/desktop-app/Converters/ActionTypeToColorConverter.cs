using System.Globalization;
using System.Windows.Data;
using System.Windows.Media;

namespace StaffControlSuite.Converters;

/// <summary>
/// Maps a punishment ActionType string to a display color brush.
/// BAN/IP_BAN = red, MUTE = yellow, WARN/KICK = orange, UNBAN/UNMUTE = green, others = gray.
/// </summary>
[ValueConversion(typeof(string), typeof(Brush))]
public class ActionTypeToColorConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        var action = value?.ToString() ?? "";
        var hex = action switch
        {
            "BAN"         => "#f38ba8",
            "TEMP_BAN"    => "#f38ba8",
            "IP_BAN"      => "#e64553",
            "TEMP_IP_BAN" => "#e64553",
            "MUTE"        => "#f9e2af",
            "TEMP_MUTE"   => "#f9e2af",
            "WARN"        => "#fab387",
            "KICK"        => "#fab387",
            "UNBAN"       => "#a6e3a1",
            "UNMUTE"      => "#a6e3a1",
            _             => "#6c7086",
        };
        return new SolidColorBrush((Color)ColorConverter.ConvertFromString(hex));
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}

/// <summary>
/// Maps a punishment status ("Active", "Revoked", "Expired") to a color brush.
/// </summary>
[ValueConversion(typeof(string), typeof(Brush))]
public class PunishmentStatusToColorConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        var status = value?.ToString() ?? "";
        var hex = status switch
        {
            "Active"  => "#a6e3a1",
            "Revoked" => "#6c7086",
            "Expired" => "#45475a",
            _         => "#6c7086",
        };
        return new SolidColorBrush((Color)ColorConverter.ConvertFromString(hex));
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}
