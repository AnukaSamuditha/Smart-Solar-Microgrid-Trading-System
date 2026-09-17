// SuperAdminSeeder.cs
// Purpose: Creates the initial Backoffice super-admin account from Seed:* configuration.
// Invoked only by the manual `dotnet run -- seed-admin` CLI command (see Program.cs) — never
// runs automatically at application startup. Idempotent: safe to run repeatedly.

using Microsoft.Extensions.Options;
using MongoDB.Driver;
using smart_solar_mgt_api.Configuration;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Auth;

namespace smart_solar_mgt_api.Services.Seed;

public class SuperAdminSeeder
{
    private readonly MongoContext _mongoContext;
    private readonly SeedOptions _seedOptions;
    private readonly IPasswordHasherService _passwordHasher;
    private readonly ILogger<SuperAdminSeeder> _logger;

    public SuperAdminSeeder(
        MongoContext mongoContext,
        IOptions<SeedOptions> seedOptions,
        IPasswordHasherService passwordHasher,
        ILogger<SuperAdminSeeder> logger)
    {
        _mongoContext = mongoContext;
        _seedOptions = seedOptions.Value;
        _passwordHasher = passwordHasher;
        _logger = logger;
    }

    // create the initial Backoffice super-admin account from configured credentials; returns a process exit code
    public async Task<int> RunAsync(CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(_seedOptions.SuperAdminEmail) ||
            string.IsNullOrWhiteSpace(_seedOptions.SuperAdminUsername) ||
            string.IsNullOrWhiteSpace(_seedOptions.SuperAdminPassword))
        {
            _logger.LogError(
                "Seed admin credentials are missing. Set Seed__SuperAdminEmail, Seed__SuperAdminUsername and " +
                "Seed__SuperAdminPassword before running: dotnet run -- seed-admin");
            return 1;
        }

        await _mongoContext.EnsureIndexesAsync(cancellationToken);

        var normalizedEmail = _seedOptions.SuperAdminEmail.Trim().ToLowerInvariant();
        var existing = await _mongoContext.Users.Find(u => u.Email == normalizedEmail).FirstOrDefaultAsync(cancellationToken);
        if (existing is not null)
        {
            _logger.LogInformation("Super admin account already exists for {Email}; nothing to do.", normalizedEmail);
            return 0;
        }

        var user = new User
        {
            Email = normalizedEmail,
            Username = _seedOptions.SuperAdminUsername,
            PasswordHash = _passwordHasher.HashPassword(_seedOptions.SuperAdminPassword),
            Role = UserRole.Backoffice,
            Status = UserStatus.Active,
            IsSeededAdmin = true,
            CreatedAt = DateTime.UtcNow,
            CreatedBy = "system"
        };

        try
        {
            await _mongoContext.Users.InsertOneAsync(user, cancellationToken: cancellationToken);
            _logger.LogInformation("Super admin account created for {Email}.", normalizedEmail);
        }
        catch (MongoWriteException ex) when (ex.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            // another concurrent run (or the unique index) beat us to it — this is still a successful outcome
            _logger.LogInformation("Super admin account was created concurrently for {Email}; nothing to do.", normalizedEmail);
        }

        return 0;
    }
}
