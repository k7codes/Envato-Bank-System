using System.Windows;

using EnvatoBank.Desktop.Services;
using EnvatoBank.Desktop.ViewModels;
using EnvatoBank.Desktop.Views;

namespace EnvatoBank.Desktop;

public partial class App : Application
{
    private ApiClient _api = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        _api = new ApiClient(AppConfig.ApiBaseUrl);
        OpenLogin();
    }

    private void OpenLogin()
    {
        var viewModel = new LoginViewModel(_api);
        var login = new LoginWindow(viewModel);
        viewModel.Authenticated += () => OpenShell(login);
        MainWindow = login;
        login.Show();
    }

    private void OpenShell(Window login)
    {
        var viewModel = new ShellViewModel(_api);
        var shell = new MainWindow(viewModel);
        viewModel.LogoutRequested += () => OpenLoginFrom(shell);
        MainWindow = shell;
        shell.Show();
        login.Close();
    }

    private void OpenLoginFrom(Window shell)
    {
        var viewModel = new LoginViewModel(_api);
        var login = new LoginWindow(viewModel);
        viewModel.Authenticated += () => OpenShell(login);
        MainWindow = login;
        login.Show();
        shell.Close();
    }
}
