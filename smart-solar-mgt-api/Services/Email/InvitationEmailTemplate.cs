// InvitationEmailTemplate.cs
// Purpose: Builds the subject/HTML body for the account-setup invitation email, styled to match
// the Wattex web app's dark theme (near-black panels, lime `#d9ff43` accent — see
// smart-solar-mgt-fe/app/globals.css and components/auth/auth-visual-panel.tsx). Shares its
// outer chrome (logo header, footer) with EmailLayout so every transactional email looks
// consistent.

using System.Net;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Email;

public static class InvitationEmailTemplate
{
    // build the subject and HTML body for an invited user's account-setup email
    public static (string Subject, string HtmlBody) Build(User user, string inviteLink, int tokenLifetimeHours)
    {
        var roleLabel = user.Role == UserRole.Backoffice ? "Backoffice user" : "Grid Operator";
        var subject = $"You're invited to Wattex as a {roleLabel}";
        var greetingName = string.IsNullOrWhiteSpace(user.Username) ? "there" : WebUtility.HtmlEncode(user.Username);
        var safeInviteLink = WebUtility.HtmlEncode(inviteLink);

        var bodyRows = $$"""
            <tr>
              <td class="wattex-padding" style="padding:24px 32px 0 32px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                <h1 style="margin:0 0 12px 0;font-size:21px;line-height:1.3;font-weight:600;color:#f5f5f5;">You're invited as a {{roleLabel}}</h1>
                <p style="margin:0 0 20px 0;font-size:14px;line-height:1.6;color:#a7b0ad;">
                  Hi {{greetingName}}, an administrator has created a Wattex account for you on the Smart Solar Microgrid Trading System. Set your password to activate it and sign in.
                </p>
              </td>
            </tr>
            <tr>
              <td class="wattex-padding" align="center" style="padding:4px 32px 28px 32px;">
                <table role="presentation" cellpadding="0" cellspacing="0">
                  <tr>
                    <td align="center" bgcolor="#d9ff43" style="border-radius:8px;">
                      <a href="{{safeInviteLink}}" style="display:inline-block;padding:12px 28px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#10130d;text-decoration:none;">Set up your account</a>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
            <tr>
              <td class="wattex-padding" style="padding:0 32px 4px 32px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                <p style="margin:0 0 4px 0;font-size:12px;color:#767f7c;">Or paste this link into your browser:</p>
                <p style="margin:0;font-size:12px;color:#8fa69c;word-break:break-all;">
                  <a href="{{safeInviteLink}}" style="color:#8fa69c;text-decoration:underline;">{{safeInviteLink}}</a>
                </p>
              </td>
            </tr>
            <tr>
              <td class="wattex-padding" style="padding:20px 32px 32px 32px;border-top:1px solid rgba(255,255,255,0.08);font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                <p style="margin:16px 0 0 0;font-size:12px;line-height:1.6;color:#767f7c;">This invitation link expires in {{tokenLifetimeHours}} hours. If you weren't expecting this email, you can safely ignore it.</p>
              </td>
            </tr>
            """;

        var html = EmailLayout.Wrap(
            subject,
            "You've been invited to join Wattex — set up your account to get started.",
            bodyRows);

        return (subject, html);
    }
}
