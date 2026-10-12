using System.Collections.ObjectModel;
using System.Windows.Input;

using EnvatoBank.Desktop.Infrastructure;
using EnvatoBank.Desktop.Models;
using EnvatoBank.Desktop.Services;

namespace EnvatoBank.Desktop.ViewModels;

public sealed class TransferViewModel : ObservableObject
{
    private readonly ApiClient _api;
    private AccountDto? _sourceAccount;
    private string _targetAccountNo = string.Empty;
    private string _amountText = string.Empty;
    private string? _description;
    private string? _statusMessage;
    private string? _errorMessage;
    private bool _isBusy;
    private string _idempotencyKey = Guid.NewGuid().ToString();

    public TransferViewModel(ApiClient api)
    {
        _api = api;
        SubmitCommand = new AsyncRelayCommand(SubmitAsync, CanSubmit);
    }

    public ObservableCollection<AccountDto> Accounts { get; } = new();

    public AccountDto? SourceAccount
    {
        get => _sourceAccount;
        set
        {
            if (SetProperty(ref _sourceAccount, value))
            {
                CommandManager.InvalidateRequerySuggested();
            }
        }
    }

    public string TargetAccountNo
    {
        get => _targetAccountNo;
        set
        {
            if (SetProperty(ref _targetAccountNo, value))
            {
                CommandManager.InvalidateRequerySuggested();
            }
        }
    }

    public string AmountText
    {
        get => _amountText;
        set
        {
            if (SetProperty(ref _amountText, value))
            {
                CommandManager.InvalidateRequerySuggested();
            }
        }
    }

    public string? Description
    {
        get => _description;
        set => SetProperty(ref _description, value);
    }

    public string? StatusMessage
    {
        get => _statusMessage;
        private set => SetProperty(ref _statusMessage, value);
    }

    public string? ErrorMessage
    {
        get => _errorMessage;
        private set => SetProperty(ref _errorMessage, value);
    }

    public bool IsBusy
    {
        get => _isBusy;
        private set => SetProperty(ref _isBusy, value);
    }

    public AsyncRelayCommand SubmitCommand { get; }

    public event Action? Completed;

    public async Task InitializeAsync()
    {
        StatusMessage = null;
        ErrorMessage = null;
        try
        {
            var list = await _api.GetAccountsAsync();
            Accounts.Clear();
            foreach (var account in list)
            {
                Accounts.Add(account);
            }
            SourceAccount ??= Accounts.FirstOrDefault();
        }
        catch (Exception ex)
        {
            ErrorMessage = "Hesaplar yüklenemedi: " + ex.Message;
        }
    }

    private bool CanSubmit()
        => !_isBusy && SourceAccount is not null
           && !string.IsNullOrWhiteSpace(_targetAccountNo)
           && !string.IsNullOrWhiteSpace(_amountText);

    private async Task SubmitAsync()
    {
        ErrorMessage = null;
        StatusMessage = null;

        if (SourceAccount is null)
        {
            ErrorMessage = "Kaynak hesap seçin.";
            return;
        }
        if (!Money.TryParseMinor(AmountText, out var amountMinor))
        {
            ErrorMessage = "Geçerli bir tutar girin (örn. 125,50).";
            return;
        }

        IsBusy = true;
        try
        {
            var request = new TransferRequestDto
            {
                SourceAccountId = SourceAccount.Id,
                TargetAccountNo = TargetAccountNo.Trim(),
                AmountMinor = amountMinor,
                IdempotencyKey = _idempotencyKey,
                Description = string.IsNullOrWhiteSpace(Description) ? null : Description.Trim()
            };

            var result = await _api.CreateTransferAsync(request);

            StatusMessage = $"Transfer tamamlandı. Referans: {result.Reference} · Tutar: {result.AmountDisplay}";
            _idempotencyKey = Guid.NewGuid().ToString();
            AmountText = string.Empty;
            TargetAccountNo = string.Empty;
            Description = null;

            Completed?.Invoke();
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception ex)
        {
            ErrorMessage = "Beklenmeyen hata: " + ex.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }
}
