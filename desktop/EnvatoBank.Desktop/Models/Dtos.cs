using System.Text.Json.Serialization;

namespace EnvatoBank.Desktop.Models;

public sealed class TokenResponse
{
    public string AccessToken { get; set; } = string.Empty;
    public string RefreshToken { get; set; } = string.Empty;
    public string TokenType { get; set; } = "Bearer";
    public long ExpiresInSeconds { get; set; }
    public string Role { get; set; } = string.Empty;
    public Guid CustomerId { get; set; }
    public string FullName { get; set; } = string.Empty;
}

public sealed class ApiError
{
    public int Status { get; set; }
    public string? Code { get; set; }
    public string? Message { get; set; }
    public string? Path { get; set; }
    public string? Timestamp { get; set; }
}

public sealed class AccountDto
{
    public Guid Id { get; set; }
    public string AccountNo { get; set; } = string.Empty;
    public Guid CustomerId { get; set; }
    public string Currency { get; set; } = string.Empty;
    public string Type { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public long BalanceMinor { get; set; }
    public string Balance { get; set; } = string.Empty;
    public DateTimeOffset CreatedAt { get; set; }
}

public sealed class LedgerEntryDto
{
    public DateTimeOffset CreatedAt { get; set; }
    public string EntryType { get; set; } = string.Empty;
    public long AmountMinor { get; set; }
    public string Currency { get; set; } = string.Empty;
    public long BalanceAfterMinor { get; set; }
    public string? Description { get; set; }
    public Guid? TransferId { get; set; }
    public Guid TransactionId { get; set; }

    [JsonIgnore]
    public string AmountDisplay => Infrastructure.Money.Signed(AmountMinor, Currency, EntryType);

    [JsonIgnore]
    public string BalanceAfterDisplay => Infrastructure.Money.Format(BalanceAfterMinor, Currency);

    [JsonIgnore]
    public string TypeDisplay => EntryType == "CREDIT" ? "Alacak" : "Borç";

    [JsonIgnore]
    public string DateDisplay => CreatedAt.LocalDateTime.ToString("g");
}

public sealed class TransferRequestDto
{
    public Guid SourceAccountId { get; set; }
    public string TargetAccountNo { get; set; } = string.Empty;
    public long AmountMinor { get; set; }
    public string IdempotencyKey { get; set; } = string.Empty;
    public string? Description { get; set; }
}

public sealed class TransferResponseDto
{
    public Guid Id { get; set; }
    public string Reference { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public Guid SourceAccountId { get; set; }
    public Guid TargetAccountId { get; set; }
    public long AmountMinor { get; set; }
    public string Currency { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset? CompletedAt { get; set; }

    [JsonIgnore]
    public string AmountDisplay => Infrastructure.Money.Format(AmountMinor, Currency);

    [JsonIgnore]
    public string DateDisplay => CreatedAt.LocalDateTime.ToString("g");
}

public sealed class PageResponse<T>
{
    public List<T> Content { get; set; } = new();
    public int Page { get; set; }
    public int Size { get; set; }
    public long TotalElements { get; set; }
    public int TotalPages { get; set; }
}
