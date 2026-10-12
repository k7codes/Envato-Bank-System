using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

using EnvatoBank.Desktop.Models;

namespace EnvatoBank.Desktop.Services;

public sealed class ApiClient
{
    private readonly HttpClient _http;
    private readonly JsonSerializerOptions _json = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    public string? AccessToken { get; private set; }
    public string? RefreshToken { get; private set; }
    public Guid CustomerId { get; private set; }
    public string Role { get; private set; } = string.Empty;
    public string FullName { get; private set; } = string.Empty;
    public bool IsAuthenticated => !string.IsNullOrEmpty(AccessToken);

    public ApiClient(string baseUrl)
    {
        _http = new HttpClient { BaseAddress = new Uri(baseUrl), Timeout = TimeSpan.FromSeconds(30) };
    }

    public async Task<TokenResponse> LoginAsync(string email, string password)
    {
        var response = await SendAsync<TokenResponse>(HttpMethod.Post, "api/auth/login",
            new { email, password }, authenticated: false, allowRetry: false);
        Apply(response);
        return response;
    }

    public async Task LogoutAsync()
    {
        try
        {
            if (IsAuthenticated)
            {
                await SendAsync<object?>(HttpMethod.Post, "api/auth/logout", null, authenticated: true);
            }
        }
        catch (ApiException)
        {
            // best effort
        }
        finally
        {
            Clear();
        }
    }

    public Task<List<AccountDto>> GetAccountsAsync()
        => SendAsync<List<AccountDto>>(HttpMethod.Get, "api/accounts", null, authenticated: true);

    public Task<List<LedgerEntryDto>> GetStatementAsync(Guid accountId, int page = 0, int size = 50)
        => SendAsync<List<LedgerEntryDto>>(HttpMethod.Get,
            $"api/accounts/{accountId}/statement?page={page}&size={size}", null, authenticated: true);

    public Task<TransferResponseDto> CreateTransferAsync(TransferRequestDto request)
        => SendAsync<TransferResponseDto>(HttpMethod.Post, "api/transfers", request, authenticated: true);

    public Task<PageResponse<TransferResponseDto>> GetTransfersAsync(int page = 0, int size = 20)
        => SendAsync<PageResponse<TransferResponseDto>>(HttpMethod.Get,
            $"api/transfers?page={page}&size={size}", null, authenticated: true);

    private void Apply(TokenResponse response)
    {
        AccessToken = response.AccessToken;
        RefreshToken = response.RefreshToken;
        CustomerId = response.CustomerId;
        Role = response.Role;
        FullName = response.FullName;
    }

    private void Clear()
    {
        AccessToken = null;
        RefreshToken = null;
        CustomerId = Guid.Empty;
        Role = string.Empty;
        FullName = string.Empty;
    }

    private async Task<bool> TryRefreshAsync()
    {
        if (string.IsNullOrEmpty(RefreshToken))
        {
            return false;
        }
        try
        {
            var response = await SendAsync<TokenResponse>(HttpMethod.Post, "api/auth/refresh",
                new { refreshToken = RefreshToken }, authenticated: false, allowRetry: false);
            Apply(response);
            return true;
        }
        catch (ApiException)
        {
            Clear();
            return false;
        }
    }

    private async Task<T> SendAsync<T>(HttpMethod method, string path, object? body,
        bool authenticated, bool allowRetry = true)
    {
        using var request = new HttpRequestMessage(method, path);
        if (body is not null)
        {
            var payload = JsonSerializer.Serialize(body, _json);
            request.Content = new StringContent(payload, Encoding.UTF8, "application/json");
        }
        if (authenticated && !string.IsNullOrEmpty(AccessToken))
        {
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", AccessToken);
        }

        using var response = await _http.SendAsync(request);

        if (response.StatusCode == HttpStatusCode.Unauthorized && authenticated && allowRetry
            && await TryRefreshAsync())
        {
            return await SendAsync<T>(method, path, body, authenticated, allowRetry: false);
        }

        if (!response.IsSuccessStatusCode)
        {
            throw await ToExceptionAsync(response);
        }

        var content = await response.Content.ReadAsStringAsync();
        if (string.IsNullOrWhiteSpace(content))
        {
            return default!;
        }
        return JsonSerializer.Deserialize<T>(content, _json)!;
    }

    private async Task<ApiException> ToExceptionAsync(HttpResponseMessage response)
    {
        var status = (int)response.StatusCode;
        try
        {
            var raw = await response.Content.ReadAsStringAsync();
            if (!string.IsNullOrWhiteSpace(raw))
            {
                var error = JsonSerializer.Deserialize<ApiError>(raw, _json);
                if (error is not null)
                {
                    return new ApiException(status, error.Code, error.Message ?? $"HTTP {status}");
                }
            }
        }
        catch (JsonException)
        {
            // fall through to generic message
        }
        return new ApiException(status, null, $"Sunucu hatası (HTTP {status})");
    }
}
