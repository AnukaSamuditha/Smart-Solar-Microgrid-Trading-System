// IJwtTokenService.cs
// Purpose: Abstraction over access-token issuance. Implemented by JwtTokenService.

using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Services.Auth;

public interface IJwtTokenService
{
    (string Token, DateTime ExpiresAtUtc) GenerateAccessToken(User user);
}
