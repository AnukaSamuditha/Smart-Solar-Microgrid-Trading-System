// ProsumerPasswordSetConfirmationEmailTemplate.cs
// Purpose: Builds the subject/HTML body for the confirmation sent after a prosumer sets their
// password via the invitation-acceptance flow. Unlike PasswordSetConfirmationEmailTemplate
// (web app Users), this has no "sign in" call-to-action — prosumers have no web login; the
// password is for a future mobile-app authentication feature (see Prosumer.PasswordHash).

using System.Net;
using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Services.Email;

public static class ProsumerPasswordSetConfirmationEmailTemplate
{
    // build the subject and HTML body for the "your password was updated" confirmation email
    public static (string Subject, string HtmlBody) Build(Prosumer prosumer)
    {
        const string subject = "Your Wattex password has been updated";
        var greetingName = string.IsNullOrWhiteSpace(prosumer.FullName) ? "there" : WebUtility.HtmlEncode(prosumer.FullName);

        var bodyRows = $$"""
            <tr>
              <td class="wattex-padding" style="padding:24px 32px 0 32px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                <h1 style="margin:0 0 12px 0;font-size:21px;line-height:1.3;font-weight:600;color:#f5f5f5;">Password updated</h1>
                <p style="margin:0 0 20px 0;font-size:14px;line-height:1.6;color:#a7b0ad;">
                  Hi {{greetingName}}, the password for your Wattex prosumer profile (NIC {{prosumer.Nic}}) was just set.
                </p>
              </td>
            </tr>
            <tr>
              <td class="wattex-padding" style="padding:0 32px 32px 32px;border-top:1px solid rgba(255,255,255,0.08);font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                <p style="margin:16px 0 0 0;font-size:12px;line-height:1.6;color:#767f7c;">If you didn't make this change, contact your grid operator or Backoffice administrator immediately.</p>
              </td>
            </tr>
            """;

        var html = EmailLayout.Wrap(
            subject,
            "Your Wattex prosumer profile password was just updated.",
            bodyRows);

        return (subject, html);
    }
}
