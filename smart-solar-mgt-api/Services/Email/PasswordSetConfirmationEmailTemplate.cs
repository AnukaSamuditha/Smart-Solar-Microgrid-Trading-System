// PasswordSetConfirmationEmailTemplate.cs
// Purpose: Builds the subject/HTML body for the security notification sent after a user
// successfully sets their password via the invitation-acceptance flow (the web frontend's
// "reset password" page, POST /api/v1/auth/accept-invitation). Shares its outer chrome with
// EmailLayout/InvitationEmailTemplate so it stays visually consistent with the rest of Wattex's
// transactional email.

using System.Net;
using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Services.Email;

public static class PasswordSetConfirmationEmailTemplate
{
    // build the subject and HTML body for the "your password was updated" confirmation email
    public static (string Subject, string HtmlBody) Build(User user, string signInLink)
    {
        const string subject = "Your Wattex password has been updated";
        var greetingName = string.IsNullOrWhiteSpace(user.Username) ? "there" : WebUtility.HtmlEncode(user.Username);
        var safeSignInLink = WebUtility.HtmlEncode(signInLink);

        var bodyRows = $$"""
            <tr>
              <td class="wattex-padding" style="padding:24px 32px 0 32px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                <h1 style="margin:0 0 12px 0;font-size:21px;line-height:1.3;font-weight:600;color:#f5f5f5;">Password updated</h1>
                <p style="margin:0 0 20px 0;font-size:14px;line-height:1.6;color:#a7b0ad;">
                  Hi {{greetingName}}, your Wattex account password was just updated. You can now sign in with your new password.
                </p>
              </td>
            </tr>
            <tr>
              <td class="wattex-padding" align="center" style="padding:4px 32px 28px 32px;">
                <table role="presentation" cellpadding="0" cellspacing="0">
                  <tr>
                    <td align="center" bgcolor="#d9ff43" style="border-radius:8px;">
                      <a href="{{safeSignInLink}}" style="display:inline-block;padding:12px 28px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#10130d;text-decoration:none;">Sign in to Wattex</a>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
            <tr>
              <td class="wattex-padding" style="padding:20px 32px 32px 32px;border-top:1px solid rgba(255,255,255,0.08);font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                <p style="margin:16px 0 0 0;font-size:12px;line-height:1.6;color:#767f7c;">If you didn't make this change, contact your administrator immediately.</p>
              </td>
            </tr>
            """;

        var html = EmailLayout.Wrap(
            subject,
            "Your Wattex account password was just updated.",
            bodyRows);

        return (subject, html);
    }
}
