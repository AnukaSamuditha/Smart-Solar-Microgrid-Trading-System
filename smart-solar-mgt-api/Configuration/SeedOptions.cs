// SeedOptions.cs
// Purpose: Binds the "Seed" configuration section, consumed only by the manual
// `dotnet run -- seed-admin` command (see SuperAdminSeeder). All three values are secrets
// and must never be committed — supply them via environment variables at invocation time.

namespace smart_solar_mgt_api.Configuration;

public class SeedOptions
{
    public const string SectionName = "Seed";

    public string? SuperAdminEmail { get; set; }

    public string? SuperAdminUsername { get; set; }

    public string? SuperAdminPassword { get; set; }
}
