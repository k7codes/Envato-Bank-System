namespace EnvatoBank.Desktop.Services;

public static class AppConfig
{
    public static string ApiBaseUrl { get; } =
        Environment.GetEnvironmentVariable("ENVATO_API_URL") ?? "http://127.0.0.1:8080/";
}
