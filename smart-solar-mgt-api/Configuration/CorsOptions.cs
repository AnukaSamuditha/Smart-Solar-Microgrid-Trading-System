// CorsOptions.cs
// Purpose: Binds the "Cors" configuration section. AllowedOrigins lists the web frontend
// origin(s) permitted to make credentialed (cookie-carrying) cross-origin requests to this
// API — required for the HttpOnly-cookie web auth flow described in
// docs/authentication-implementation-approach.md, section 6.

namespace smart_solar_mgt_api.Configuration;

public class CorsOptions
{
    public const string SectionName = "Cors";

    public string[] AllowedOrigins { get; set; } = [];
}
