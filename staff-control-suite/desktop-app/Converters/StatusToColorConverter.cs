using System.Globalization;
using System.Windows.Data;
using System.Windows.Media;

namespace StaffControlSuite.Converters;

/// <summary>
/// Converts a bool (online/offline) to a SolidColorBrush or Color.
/// true/online → green, false/offline → red.
/// Also handles string "Online"/"Offline".
/// </summary>
[ValueConversion(typeof(bool), typeof(SolidColorBrush))]
public class StatusToColorConverter : IValueConverter
{
    private static readonly SolidColorBrush OnlineBrush =
        new(Color.FromRgb(0x4c, 0xaf, 0x50));  // #4caf50

    private static readonly SolidColorBrush OfflineBrush =
        new(Color.FromRgb(0xf4, 0x43, 0x36));  // #f44336

    private static readonly Color OnlineColor =
        Color.FromRgb(0x4c, 0xaf, 0x50);

    private static readonly Color OfflineColor =
        Color.FromRgb(0xf4, 0x43, 0x36);

    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        bool isOnline = value switch
        {
            bool b => b,
            string s => s.Equals("Online", StringComparison.OrdinalIgnoreCase),
            _ => false
        };

        if (targetType == typeof(Color))
            return isOnline ? OnlineColor : OfflineColor;

        return isOnline ? OnlineBrush : OfflineBrush;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}
