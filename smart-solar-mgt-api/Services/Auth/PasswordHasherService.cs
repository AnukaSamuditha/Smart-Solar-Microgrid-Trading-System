// PasswordHasherService.cs
// Purpose: Wraps ASP.NET Core's framework-provided PBKDF2 PasswordHasher<TUser>, used
// standalone (not the full Identity membership system) so it fits the custom Mongo-backed,
// invitation-driven account flow. Same hashing path for the seeded admin and invited users.

using Microsoft.AspNetCore.Identity;
using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Services.Auth;

public class PasswordHasherService : IPasswordHasherService
{
    private readonly PasswordHasher<User> _hasher = new();

    // hash a plaintext password; the default hasher does not read the user argument, so a dummy instance is safe
    public string HashPassword(string password) =>
        _hasher.HashPassword(new User(), password);

    // verify a plaintext password against a previously hashed value, treating a recommended rehash as success
    public bool VerifyPassword(string hashedPassword, string providedPassword)
    {
        var result = _hasher.VerifyHashedPassword(new User(), hashedPassword, providedPassword);
        return result is PasswordVerificationResult.Success or PasswordVerificationResult.SuccessRehashNeeded;
    }
}
