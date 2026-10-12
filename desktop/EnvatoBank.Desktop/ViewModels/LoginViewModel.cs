using EnvatoBank.Desktop.Infrastructure;
using EnvatoBank.Desktop.Services;

namespace EnvatoBank.Desktop.ViewModels;

public sealed class LoginViewModel : ObservableObject
{
    private readonly ApiClient _api;
    private string _email = "ayse@example.com";
    private string _password = "Demo!2345";
    private string? _errorMessage;
    private bool _isBusy;

    public LoginViewModel(ApiClient api)
    {
        _api = api;
        LoginCommand = new AsyncRelayCommand(LoginAsync, CanLogin);
    }

    public string Email
    {
        get => _email;
        set => SetProperty(ref _email, value);
    }

    public string Password
    {
        get => _password;
        set => SetProperty(ref _password, value);
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

    public AsyncRelayCommand LoginCommand { get; }

    public event Action? Authenticated;

    private bool CanLogin()
        => !_isBusy && !string.IsNullOrWhiteSpace(_email) && !string.IsNullOrWhiteSpace(_password);

    private async Task LoginAsync()
    {
        ErrorMessage = null;
        IsBusy = true;
        try
        {
            await _api.LoginAsync(Email.Trim(), Password);
            Authenticated?.Invoke();
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception ex)
        {
            ErrorMessage = "Sunucuya bağlanılamadı. API çalışıyor mu? (" + ex.Message + ")";
        }
        finally
        {
            IsBusy = false;
        }
    }
}
