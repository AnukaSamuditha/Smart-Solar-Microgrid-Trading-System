// UserResponse.cs
// Purpose: Public-facing projection of a User document — omits PasswordHash and other
// internal fields. Returned by GET /me, the list endpoint, and account creation.

using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Models.Dtos;

public record UserResponse(string Id, string Email, string? Username, string Role, string Status)
{
    // project a User entity onto its public response shape
    public static UserResponse FromEntity(User user) =>
        new(user.Id, user.Email, user.Username, user.Role.ToString(), user.Status.ToString());
}
