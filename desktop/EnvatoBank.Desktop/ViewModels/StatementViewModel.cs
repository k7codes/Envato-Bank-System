using System.Collections.ObjectModel;

using EnvatoBank.Desktop.Infrastructure;
using EnvatoBank.Desktop.Models;
using EnvatoBank.Desktop.Services;

namespace EnvatoBank.Desktop.ViewModels;

public sealed class StatementViewModel : ObservableObject
{
    private readonly ApiClient _api;
    private bool _isBusy;
    private string? _errorMessage;

    public StatementViewModel(ApiClient api, AccountDto account)
    {
        _api = api;
        Account = account;
        BackCommand = new RelayCommand(() => BackRequested?.Invoke());
        ReloadCommand = new AsyncRelayCommand(LoadAsync);
    }

    public AccountDto Account { get; }

    public ObservableCollection<LedgerEntryDto> Entries { get; } = new();

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

    public RelayCommand BackCommand { get; }

    public AsyncRelayCommand ReloadCommand { get; }

    public string Header => $"{Account.AccountNo}  ·  {Account.Type}  ·  {Account.Balance}";

    public event Action? BackRequested;

    public async Task LoadAsync()
    {
        ErrorMessage = null;
        IsBusy = true;
        try
        {
            var list = await _api.GetStatementAsync(Account.Id);
            Entries.Clear();
            foreach (var entry in list)
            {
                Entries.Add(entry);
            }
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception ex)
        {
            ErrorMessage = "Hareketler yüklenemedi: " + ex.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }
}
