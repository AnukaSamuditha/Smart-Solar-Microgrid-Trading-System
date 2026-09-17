// IPasswordHasherService.cs
// Purpose: Abstraction over password hashing so callers never depend on the concrete
// algorithm. Implemented by PasswordHasherService.

namespace smart_solar_mgt_api.Services.Auth;

public interface IPasswordHasherService
{
    string HashPassword(string password);

    bool VerifyPassword(string hashedPassword, string providedPassword);
}
