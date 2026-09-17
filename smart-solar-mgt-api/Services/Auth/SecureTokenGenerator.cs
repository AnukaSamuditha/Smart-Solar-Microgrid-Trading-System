// SecureTokenGenerator.cs
// Purpose: Shared cryptographic pattern for both refresh tokens and invitation tokens —
// generate 256 bits of randomness, and persist only a SHA-256 hash so the raw value is never
// stored at rest. See docs/authentication-implementation-approach.md, section 8.

using System.Security.Cryptography;
using System.Text;

namespace smart_solar_mgt_api.Services.Auth;

public static class SecureTokenGenerator
{
    // generate a cryptographically secure random token, base64url-encoded for safe transport in URLs/JSON
    public static string GenerateToken()
    {
        var bytes = RandomNumberGenerator.GetBytes(32);
        return Convert.ToBase64String(bytes)
            .TrimEnd('=')
            .Replace('+', '-')
            .Replace('/', '_');
    }

    // hash a raw token with SHA-256 so only the hash is ever persisted
    public static string Hash(string rawToken)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(rawToken));
        return Convert.ToHexString(bytes);
    }
}
