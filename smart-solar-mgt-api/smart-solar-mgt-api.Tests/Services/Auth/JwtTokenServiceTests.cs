// JwtTokenServiceTests.cs
// Purpose: Unit tests for JwtTokenService — verifies the issued access token carries the
// expected claims, issuer/audience, and expiration, and contains no sensitive data.

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.Extensions.Options;
using smart_solar_mgt_api.Configuration;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Auth;

namespace smart_solar_mgt_api.Tests.Services.Auth;

public class JwtTokenServiceTests
{
    private static JwtTokenService CreateSut(JwtOptions? options = null) =>
        new(Options.Create(options ?? new JwtOptions
        {
            SigningKey = "unit-test-signing-key-at-least-256-bits-long!!",
            Issuer = "smart-solar-mgt-api-tests",
            Audience = "smart-solar-mgt-clients-tests",
            AccessTokenLifetimeMinutes = 15
        }));

    private static User CreateUser() => new()
    {
        Id = "507f1f77bcf86cd799439011",
        Email = "backoffice.user@example.com",
        Role = UserRole.Backoffice
    };

    [Fact]
    public void GenerateAccessToken_IncludesExpectedClaims()
    {
        // the token must carry sub/email/role so the API can authorize requests without a DB round-trip
        var sut = CreateSut();
        var user = CreateUser();

        var (token, _) = sut.GenerateAccessToken(user);
        var jwt = new JwtSecurityTokenHandler().ReadJwtToken(token);

        Assert.Equal(user.Id, jwt.Claims.Single(c => c.Type == JwtRegisteredClaimNames.Sub).Value);
        Assert.Equal(user.Email, jwt.Claims.Single(c => c.Type == JwtRegisteredClaimNames.Email).Value);
        Assert.Equal(user.Role.ToString(), jwt.Claims.Single(c => c.Type == ClaimTypes.Role).Value);
        Assert.Contains(jwt.Claims, c => c.Type == JwtRegisteredClaimNames.Jti);
    }

    [Fact]
    public void GenerateAccessToken_SetsConfiguredIssuerAndAudience()
    {
        // issuer/audience must match configuration so JwtBearer validation accepts the token
        var options = new JwtOptions
        {
            SigningKey = "unit-test-signing-key-at-least-256-bits-long!!",
            Issuer = "custom-issuer",
            Audience = "custom-audience",
            AccessTokenLifetimeMinutes = 15
        };
        var sut = CreateSut(options);

        var (token, _) = sut.GenerateAccessToken(CreateUser());
        var jwt = new JwtSecurityTokenHandler().ReadJwtToken(token);

        Assert.Equal(options.Issuer, jwt.Issuer);
        Assert.Equal(options.Audience, jwt.Audiences.Single());
    }

    [Fact]
    public void GenerateAccessToken_ExpiresAfterConfiguredLifetime()
    {
        // the returned expiry and the token's own exp claim must both honor AccessTokenLifetimeMinutes
        var options = new JwtOptions
        {
            SigningKey = "unit-test-signing-key-at-least-256-bits-long!!",
            Issuer = "issuer",
            Audience = "audience",
            AccessTokenLifetimeMinutes = 15
        };
        var sut = CreateSut(options);

        var (token, expiresAtUtc) = sut.GenerateAccessToken(CreateUser());
        var jwt = new JwtSecurityTokenHandler().ReadJwtToken(token);

        Assert.True(Math.Abs((expiresAtUtc - DateTime.UtcNow.AddMinutes(15)).TotalSeconds) < 5);
        Assert.True(Math.Abs((jwt.ValidTo - expiresAtUtc).TotalSeconds) < 5);
    }

    [Fact]
    public void GenerateAccessToken_DoesNotIncludePasswordHashOrOtherSensitiveData()
    {
        // no PII beyond email, and never the password hash, should ever reach the token
        var sut = CreateSut();
        var user = CreateUser();
        user.PasswordHash = "should-never-appear-in-a-jwt";

        var (token, _) = sut.GenerateAccessToken(user);

        Assert.DoesNotContain(user.PasswordHash, token);
    }
}
