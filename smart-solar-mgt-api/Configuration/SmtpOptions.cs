// SmtpOptions.cs
// Purpose: Binds the "Smtp" configuration section, consumed by SmtpEmailSender. Points at
// MailHog in local development (see docker-compose.local.yml); User/Password are supplied
// via dotnet user-secrets or environment variables and are blank for MailHog, which needs
// no authentication.

namespace smart_solar_mgt_api.Configuration;

public class SmtpOptions
{
    public const string SectionName = "Smtp";

    public string Host { get; set; } = string.Empty;

    public int Port { get; set; } = 1025;

    public string From { get; set; } = string.Empty;

    public string? User { get; set; }

    public string? Password { get; set; }

    public bool UseSsl { get; set; }
}
