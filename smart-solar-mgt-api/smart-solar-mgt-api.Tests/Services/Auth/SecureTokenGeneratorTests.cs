// SecureTokenGeneratorTests.cs
// Purpose: Unit tests for SecureTokenGenerator — the shared pattern behind both refresh and
// invitation tokens. Verifies tokens are unique, URL-safe, and hashing is deterministic.

using smart_solar_mgt_api.Services.Auth;

namespace smart_solar_mgt_api.Tests.Services.Auth;

public class SecureTokenGeneratorTests
{
    [Fact]
    public void GenerateToken_ProducesUrlSafeString()
    {
        // base64url must not contain '+', '/', or padding, since the token travels in URLs and JSON
        var token = SecureTokenGenerator.GenerateToken();

        Assert.DoesNotContain('+', token);
        Assert.DoesNotContain('/', token);
        Assert.DoesNotContain('=', token);
    }

    [Fact]
    public void GenerateToken_SuccessiveCallsProduceDifferentTokens()
    {
        // each call must draw fresh randomness so tokens are never predictable or reused
        var first = SecureTokenGenerator.GenerateToken();
        var second = SecureTokenGenerator.GenerateToken();

        Assert.NotEqual(first, second);
    }

    [Fact]
    public void Hash_IsDeterministicForTheSameInput()
    {
        // the same raw token must always hash to the same value, so a presented token can be looked up
        var token = SecureTokenGenerator.GenerateToken();

        Assert.Equal(SecureTokenGenerator.Hash(token), SecureTokenGenerator.Hash(token));
    }

    [Fact]
    public void Hash_DiffersForDifferentInputs()
    {
        // distinct tokens must hash to distinct values to avoid collisions in the lookup index
        var first = SecureTokenGenerator.GenerateToken();
        var second = SecureTokenGenerator.GenerateToken();

        Assert.NotEqual(SecureTokenGenerator.Hash(first), SecureTokenGenerator.Hash(second));
    }
}
