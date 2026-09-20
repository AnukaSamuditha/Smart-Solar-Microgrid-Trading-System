// ProsumerMobileApprovalEmailTemplate.cs
// Purpose: Builds the subject/HTML body for a self-registered prosumer's approval notification.
// Unlike InvitationEmailTemplate/ProsumerInvitationEmailTemplate (a clickable web link into the
// Next.js frontend's /reset-password page), this prosumer registered from the mobile app and
// sets their password there too - so the button here is a wattex://reset-password deep link that
// opens the app straight into its Reset Password screen with the token pre-filled (see
// ui/auth/ResetPasswordActivity's intent-filter). The raw code is also shown as a fallback for
// opening the email somewhere the app/link can't handle it (e.g. a desktop mail client).

using System.Net;
using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Services.Email;

public static class ProsumerMobileApprovalEmailTemplate
{
    // build the subject and HTML body for an approved self-registered prosumer's reset-password link/code
    public static (string Subject, string HtmlBody) Build(Prosumer prosumer, string resetCode, int tokenLifetimeHours)
    {
        const string subject = "Your Wattex account request was approved";
        var greetingName = string.IsNullOrWhiteSpace(prosumer.FullName) ? "there" : WebUtility.HtmlEncode(prosumer.FullName);
        var deepLink = $"wattex://reset-password?token={Uri.EscapeDataString(resetCode)}";
        var safeDeepLink = WebUtility.HtmlEncode(deepLink);
        var safeResetCode = WebUtility.HtmlEncode(resetCode);

        var bodyRows = $$"""
            <tr>
              <td class="wattex-padding" style="padding:24px 32px 0 32px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                <h1 style="margin:0 0 12px 0;font-size:21px;line-height:1.3;font-weight:600;color:#f5f5f5;">Your account request was approved</h1>
                <p style="margin:0 0 20px 0;font-size:14px;line-height:1.6;color:#a7b0ad;">
                  Hi {{greetingName}}, your prosumer account request (NIC {{prosumer.Nic}}) has been reviewed and approved. Tap the button below on your phone to open the Wattex app and set your password.
                </p>
              </td>
            </tr>
            <tr>
              <td class="wattex-padding" align="center" style="padding:4px 32px 28px 32px;">
                <table role="presentation" cellpadding="0" cellspacing="0">
                  <tr>
                    <td align="center" bgcolor="#d9ff43" style="border-radius:8px;">
                      <a href="{{safeDeepLink}}" style="display:inline-block;padding:12px 28px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#10130d;text-decoration:none;">Set up your password</a>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
            <tr>
              <td class="wattex-padding" style="padding:0 32px 4px 32px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                <p style="margin:0 0 4px 0;font-size:12px;color:#767f7c;">Opening this on a computer, or the button not working? Open the Wattex app, go to Reset Password, and enter this code:</p>
                <p style="margin:0;font-size:14px;font-weight:700;letter-spacing:1px;color:#8fa69c;word-break:break-all;">{{safeResetCode}}</p>
              </td>
            </tr>
            <tr>
              <td class="wattex-padding" style="padding:20px 32px 32px 32px;border-top:1px solid rgba(255,255,255,0.08);font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                <p style="margin:16px 0 0 0;font-size:12px;line-height:1.6;color:#767f7c;">This link/code expires in {{tokenLifetimeHours}} hours. If you weren't expecting this email, you can safely ignore it.</p>
              </td>
            </tr>
            """;

        var html = EmailLayout.Wrap(
            subject,
            "Your Wattex prosumer account request was approved — set your password in the app to get started.",
            bodyRows);

        return (subject, html);
    }
}
