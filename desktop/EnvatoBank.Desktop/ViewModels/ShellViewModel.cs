using EnvatoBank.Desktop.Infrastructure;
using EnvatoBank.Desktop.Models;
using EnvatoBank.Desktop.Services;

namespace EnvatoBank.Desktop.ViewModels;

public sealed class ShellViewModel : ObservableObject
{
    private readonly ApiClient _api;
    private readonly AccountsViewModel _accounts;
    private readonly TransferViewModel _transfer;
    private object? _currentView;

    public ShellViewModel(ApiClient api)
    {
        _api = api;
        _accounts = new AccountsViewModel(api);
        _accounts.OpenStatementRequested += ShowStatement;
        _transfer = new TransferViewModel(api);
        _transfer.Completed += OnTransferCompleted;

        ShowAccountsCommand = new RelayCommand(ShowAccounts);
        ShowTransferCommand = new RelayCommand(ShowTransfer);
        LogoutCommand = new AsyncRelayCommand(LogoutAsync);

        UserLine = $"{api.FullName}  ·  {RoleLabel(api.Role)}";
        ShowAccounts();
    }

    public object? CurrentView
    {
        get => _currentView;
        private set => SetProperty(ref _currentView, value);
    }

    public string UserLine { get; }

    public RelayCommand ShowAccountsCommand { get; }

    public RelayCommand ShowTransferCommand { get; }

    public AsyncRelayCommand LogoutCommand { get; }

    public event Action? LogoutRequested;

    private void ShowAccounts()
    {
        CurrentView = _accounts;
        _ = _accounts.LoadAsync();
    }

    private void ShowTransfer()
    {
        CurrentView = _transfer;
        _ = _transfer.InitializeAsync();
    }

    private void ShowStatement(AccountDto account)
    {
        var viewModel = new StatementViewModel(_api, account);
        viewModel.BackRequested += ShowAccounts;
        CurrentView = viewModel;
        _ = viewModel.LoadAsync();
    }

    private void OnTransferCompleted()
    {
        _ = _accounts.LoadAsync();
    }

    private async Task LogoutAsync()
    {
        await _api.LogoutAsync();
        LogoutRequested?.Invoke();
    }

    private static string RoleLabel(string role) => role switch
    {
        "ADMIN" => "Yönetici",
        "EMPLOYEE" => "Banka Personeli",
        "CUSTOMER" => "Müşteri",
        _ => role
    };
}
