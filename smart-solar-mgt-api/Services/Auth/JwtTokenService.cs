// JwtTokenService.cs
// Purpose: Issues short-lived, HMAC-SHA256-signed JWT access tokens carrying only the claims
// needed to authorize a request (sub, email, role, jti). See
// docs/authentication-implementation-approach.md, section 6 for the signing rationale.

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using smart_solar_mgt_api.Authorization;
using smart_solar_mgt_api.Configuration;
using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Services.Auth;

public class JwtTokenService : IJwtTokenService
{
    private readonly JwtOptions _options;

    public JwtTokenService(IOptions<JwtOptions> options)
    {
        _options = options.Value;
    }

    // build and sign a short-lived JWT access token for the given staff user
    public (string Token, DateTime ExpiresAtUtc) GenerateAccessToken(User user) =>
        GenerateAccessToken(user.Id, user.Email, user.Role.ToString());

    // build and sign a short-lived JWT access token for the given prosumer (role claim is
    // always RoleNames.Prosumer - no per-prosumer role variation, unlike staff Users)
    public (string Token, DateTime ExpiresAtUtc) GenerateAccessToken(Prosumer prosumer) =>
        GenerateAccessToken(prosumer.Nic, prosumer.Email, RoleNames.Prosumer);

    // shared claim-set/signing logic for both account types - sub/email/role/jti only, per the
    // file-level purpose comment above
    private (string Token, DateTime ExpiresAtUtc) GenerateAccessToken(string subject, string email, string role)
    {
        var expiresAtUtc = DateTime.UtcNow.AddMinutes(_options.AccessTokenLifetimeMinutes);

        var claims = new[]
        {
            new Claim(JwtRegisteredClaimNames.Sub, subject),
            new Claim(JwtRegisteredClaimNames.Email, email),
            new Claim(ClaimTypes.Role, role),
            new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
        };

        var signingKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_options.SigningKey));
        var credentials = new SigningCredentials(signingKey, SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            issuer: _options.Issuer,
            audience: _options.Audience,
            claims: claims,
            expires: expiresAtUtc,
            signingCredentials: credentials);

        return (new JwtSecurityTokenHandler().WriteToken(token), expiresAtUtc);
    }
}
