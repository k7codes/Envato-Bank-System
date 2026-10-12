using System.Collections.ObjectModel;
using System.Windows.Input;

using EnvatoBank.Desktop.Infrastructure;
using EnvatoBank.Desktop.Models;
using EnvatoBank.Desktop.Services;

namespace EnvatoBank.Desktop.ViewModels;

public sealed class AccountsViewModel : ObservableObject
{
    private readonly ApiClient _api;
    private AccountDto? _selected;
    private bool _isBusy;
    private string? _errorMessage;

    public AccountsViewModel(ApiClient api)
    {
        _api = api;
        ReloadCommand = new AsyncRelayCommand(LoadAsync);
        OpenStatementCommand = new RelayCommand(OpenStatement, () => Selected is not null);
    }

    public ObservableCollection<AccountDto> Accounts { get; } = new();

    public AccountDto? Selected
    {
        get => _selected;
        set
        {
            if (SetProperty(ref _selected, value))
            {
                CommandManager.InvalidateRequerySuggested();
            }
        }
    }

    public bool IsBusy
    {
        get => _isBusy;
        private set => SetProperty(ref _isBusy, value);
    }

    public string? ErrorMessage
    {
        get => _errorMessage;
        private set => SetProperty(ref _errorMessage, value);
    }

    public AsyncRelayCommand ReloadCommand { get; }

    public RelayCommand OpenStatementCommand { get; }

    public event Action<AccountDto>? OpenStatementRequested;

    public async Task LoadAsync()
    {
        ErrorMessage = null;
        IsBusy = true;
        try
        {
            var list = await _api.GetAccountsAsync();
            Accounts.Clear();
            foreach (var account in list)
            {
                Accounts.Add(account);
            }
            Selected = Accounts.FirstOrDefault();
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception ex)
        {
            ErrorMessage = "Hesaplar yüklenemedi: " + ex.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }

    private void OpenStatement()
    {
        if (Selected is not null)
        {
            OpenStatementRequested?.Invoke(Selected);
        }
    }
}
