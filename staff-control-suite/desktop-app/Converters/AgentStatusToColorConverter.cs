using System.Globalization;
using System.Windows.Data;
using System.Windows.Media;

namespace StaffControlSuite.Converters;

[ValueConversion(typeof(string), typeof(SolidColorBrush))]
public class AgentStatusToColorConverter : IValueConverter
{
    private static readonly SolidColorBrush OnlineBrush    = new(Color.FromRgb(0xa6, 0xe3, 0xa1)); // green
    private static readonly SolidColorBrush ConnectingBrush = new(Color.FromRgb(0xf9, 0xe2, 0xaf)); // yellow
    private static readonly SolidColorBrush ErrorBrush     = new(Color.FromRgb(0xf3, 0x8b, 0xa8)); // red
    private static readonly SolidColorBrush OfflineBrush   = new(Color.FromRgb(0x6c, 0x70, 0x86)); // gray

    public object Convert(object value, Type targetType, object parameter, CultureInfo culture) =>
        (value as string)?.ToUpperInvariant() switch
        {
            "ONLINE"     => OnlineBrush,
            "CONNECTING" => ConnectingBrush,
            "ERROR"      => ErrorBrush,
            _            => OfflineBrush,
        };

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}
