// SmtpEmailSender.cs
// Purpose: Sends transactional email over SMTP via MailKit. Targets MailHog in local
// development (see docker-compose.local.yml); swapping to a production provider means
// adding a new IEmailSender implementation, not changing this one or its callers.

using MailKit.Net.Smtp;
using Microsoft.Extensions.Options;
using MimeKit;
using smart_solar_mgt_api.Configuration;

namespace smart_solar_mgt_api.Services.Email;

public class SmtpEmailSender : IEmailSender
{
    private readonly SmtpOptions _options;

    public SmtpEmailSender(IOptions<SmtpOptions> options)
    {
        _options = options.Value;
    }

    // connect to the configured SMTP server and send a single HTML email
    public async Task SendAsync(string to, string subject, string htmlBody, CancellationToken cancellationToken = default)
    {
        var message = new MimeMessage();
        message.From.Add(MailboxAddress.Parse(_options.From));
        message.To.Add(MailboxAddress.Parse(to));
        message.Subject = subject;
        message.Body = new TextPart("html") { Text = htmlBody };

        using var client = new SmtpClient();
        await client.ConnectAsync(_options.Host, _options.Port, _options.UseSsl, cancellationToken);

        if (!string.IsNullOrEmpty(_options.User))
        {
            await client.AuthenticateAsync(_options.User, _options.Password ?? string.Empty, cancellationToken);
        }

        await client.SendAsync(message, cancellationToken);
        await client.DisconnectAsync(true, cancellationToken);
    }
}
