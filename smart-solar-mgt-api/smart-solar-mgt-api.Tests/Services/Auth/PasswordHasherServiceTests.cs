// PasswordHasherServiceTests.cs
// Purpose: Unit tests for PasswordHasherService — verifies correct passwords are accepted,
// incorrect ones are rejected, and hashing is salted (non-deterministic).

using smart_solar_mgt_api.Services.Auth;

namespace smart_solar_mgt_api.Tests.Services.Auth;

public class PasswordHasherServiceTests
{
    private readonly PasswordHasherService _sut = new();

    [Fact]
    public void VerifyPassword_WithCorrectPassword_ReturnsTrue()
    {
        // a password hashed and then verified with the same plaintext should succeed
        var hash = _sut.HashPassword("Correct-Horse-Battery-Staple1");

        Assert.True(_sut.VerifyPassword(hash, "Correct-Horse-Battery-Staple1"));
    }

    [Fact]
    public void VerifyPassword_WithIncorrectPassword_ReturnsFalse()
    {
        // verification must fail for any plaintext other than the one that was hashed
        var hash = _sut.HashPassword("Correct-Horse-Battery-Staple1");

        Assert.False(_sut.VerifyPassword(hash, "wrong-password"));
    }

    [Fact]
    public void HashPassword_CalledTwiceWithSamePassword_ProducesDifferentHashes()
    {
        // PBKDF2 salts each hash, so hashing the same password twice must not be identical
        var first = _sut.HashPassword("same-password");
        var second = _sut.HashPassword("same-password");

        Assert.NotEqual(first, second);
    }
}
