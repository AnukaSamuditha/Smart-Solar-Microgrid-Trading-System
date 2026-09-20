// RoleNames.cs
// Purpose: Single source of truth for role name strings, used both as JWT role claim values
// and as ASP.NET Core authorization policy names, so they never appear as magic strings
// scattered across endpoints.

namespace smart_solar_mgt_api.Authorization;

public static class RoleNames
{
    public const string Backoffice = "Backoffice";

    public const string GridOperator = "GridOperator";

    // not used as an authorization-policy name (no Prosumer-only endpoints exist yet) - only as
    // the JWT role claim value issued by JwtTokenService.GenerateAccessToken(Prosumer)
    public const string Prosumer = "Prosumer";
}
