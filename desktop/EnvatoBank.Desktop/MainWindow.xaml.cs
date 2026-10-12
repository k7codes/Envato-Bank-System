using System.Windows;

using EnvatoBank.Desktop.ViewModels;

namespace EnvatoBank.Desktop;

public partial class MainWindow : Window
{
    public MainWindow(ShellViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }
}
