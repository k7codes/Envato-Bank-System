using System.Windows.Controls;
using System.Windows.Input;

using EnvatoBank.Desktop.ViewModels;

namespace EnvatoBank.Desktop.Views;

public partial class AccountsView : UserControl
{
    public AccountsView()
    {
        InitializeComponent();
    }

    private void OnRowDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (DataContext is AccountsViewModel viewModel && viewModel.Selected is not null)
        {
            viewModel.OpenStatementCommand.Execute(null);
        }
    }
}
