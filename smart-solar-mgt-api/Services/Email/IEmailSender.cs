// IEmailSender.cs
// Purpose: Generic transactional email abstraction, deliberately unaware of invitations or
// any other specific email type, so a production provider can replace SmtpEmailSender later
// with a one-class change and no impact on callers.

namespace smart_solar_mgt_api.Services.Email;

public interface IEmailSender
{
    Task SendAsync(string to, string subject, string htmlBody, CancellationToken cancellationToken = default);
}
