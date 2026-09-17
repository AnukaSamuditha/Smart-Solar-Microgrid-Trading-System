// JwtOptions.cs
// Purpose: Binds the "Jwt" configuration section. SigningKey must never be committed —
// it is supplied via dotnet user-secrets locally or the Jwt__SigningKey environment
// variable in production. See docs/authentication-implementation-approach.md, section 9.

namespace smart_solar_mgt_api.Configuration;

public class JwtOptions
{
    public const string SectionName = "Jwt";

    public string SigningKey { get; set; } = string.Empty;

    public string Issuer { get; set; } = string.Empty;

    public string Audience { get; set; } = string.Empty;

    public int AccessTokenLifetimeMinutes { get; set; } = 15;

    public int RefreshTokenLifetimeDays { get; set; } = 7;
}
