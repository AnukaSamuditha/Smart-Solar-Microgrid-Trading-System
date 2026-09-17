// InvitationOptions.cs
// Purpose: Binds the "Invitation" configuration section. FrontendBaseUrl is used to build
// the setup link embedded in invitation emails and will point at the web app once it exists.

namespace smart_solar_mgt_api.Configuration;

public class InvitationOptions
{
    public const string SectionName = "Invitation";

    public int TokenLifetimeHours { get; set; } = 48;

    public string FrontendBaseUrl { get; set; } = string.Empty;
}
