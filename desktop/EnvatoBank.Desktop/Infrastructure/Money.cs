using System.Globalization;

namespace EnvatoBank.Desktop.Infrastructure;

public static class Money
{
    private static readonly CultureInfo Tr = CultureInfo.GetCultureInfo("tr-TR");

    public static string Format(long minorUnits, string currency)
        => (minorUnits / 100m).ToString("N2", Tr) + " " + currency;

    public static string Signed(long minorUnits, string currency, string entryType)
    {
        var prefix = entryType == "CREDIT" ? "+" : "-";
        return prefix + Format(Math.Abs(minorUnits), currency);
    }

    public static bool TryParseMinor(string? text, out long minorUnits)
    {
        minorUnits = 0;
        if (string.IsNullOrWhiteSpace(text))
        {
            return false;
        }
        var normalized = text.Trim().Replace('.', ',').Replace(" ", string.Empty);
        if (!decimal.TryParse(normalized, NumberStyles.Number, Tr, out var amount) || amount <= 0)
        {
            return false;
        }
        minorUnits = (long)decimal.Round(amount * 100m, 0, MidpointRounding.AwayFromZero);
        return minorUnits > 0;
    }
}
