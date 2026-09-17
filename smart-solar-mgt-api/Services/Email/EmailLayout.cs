// EmailLayout.cs
// Purpose: Shared HTML shell (Wattex-branded dark card, logo header, footer) that individual
// email templates (InvitationEmailTemplate, PasswordSetConfirmationEmailTemplate, ...) wrap
// their own body content in, so every transactional email stays visually consistent. Markup is
// table-based with inline styles for broad email-client compatibility (see
// InvitationEmailTemplate.cs for the original design this was extracted from).

namespace smart_solar_mgt_api.Services.Email;

public static class EmailLayout
{
    // wrap the given body row(s) (as <tr> markup) in the shared Wattex email chrome
    public static string Wrap(string title, string preheader, string bodyRowsHtml)
    {
        var year = DateTime.UtcNow.Year;

        return $$"""
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <meta name="color-scheme" content="dark" />
              <title>{{title}}</title>
              <style>
                body, table, td { margin: 0; padding: 0; }
                a { color: inherit; }
                @media only screen and (max-width: 480px) {
                  .wattex-container { width: 100% !important; }
                  .wattex-padding { padding-left: 20px !important; padding-right: 20px !important; }
                }
              </style>
            </head>
            <body style="margin:0;padding:0;background-color:#0a0a0a;">
              <div style="display:none;max-height:0;overflow:hidden;opacity:0;">
                {{preheader}}
              </div>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" bgcolor="#0a0a0a" style="background-color:#0a0a0a;">
                <tr>
                  <td align="center" style="padding:40px 16px;">
                    <table role="presentation" class="wattex-container" width="480" cellpadding="0" cellspacing="0" bgcolor="#161616" style="background-color:#161616;border-radius:16px;border:1px solid rgba(255,255,255,0.08);max-width:480px;width:100%;">
                      <tr>
                        <td class="wattex-padding" style="padding:32px 32px 0 32px;">
                          <table role="presentation" cellpadding="0" cellspacing="0">
                            <tr>
                              <td style="width:36px;height:36px;background-color:#d9ff43;border-radius:9px;text-align:center;vertical-align:middle;font-size:17px;line-height:36px;">⚡</td>
                              <td style="padding-left:10px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;font-size:16px;font-weight:700;color:#f5f5f5;">Wattex</td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      {{bodyRowsHtml}}
                    </table>
                    <table role="presentation" width="480" class="wattex-container" cellpadding="0" cellspacing="0" style="max-width:480px;width:100%;">
                      <tr>
                        <td align="center" style="padding:20px 32px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                          <p style="margin:0;font-size:11px;color:#57605d;">&copy; {{year}} Wattex &middot; Smart Solar Microgrid Trading System</p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;
    }
}
