using System.Windows.Controls;
using System.Windows.Input;
using StaffControlSuite.ViewModels;

namespace StaffControlSuite.Views;

public partial class ConsoleView : UserControl
{
    private readonly ConsoleViewModel _viewModel;

    public ConsoleView()
    {
        InitializeComponent();
        _viewModel = new ConsoleViewModel();
        DataContext = _viewModel;

        // Auto-scroll when new lines are added
        _viewModel.ConsoleLines.CollectionChanged += (s, e) =>
        {
            if (_viewModel.AutoScroll)
            {
                ConsoleScroll.ScrollToBottom();
            }
        };

        Loaded += async (s, e) => await _viewModel.LoadServersAsync();
    }

    private void CommandInput_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter && _viewModel.SendCommandCommand.CanExecute(null))
        {
            _viewModel.SendCommandCommand.Execute(null);
            e.Handled = true;
        }
    }
}
