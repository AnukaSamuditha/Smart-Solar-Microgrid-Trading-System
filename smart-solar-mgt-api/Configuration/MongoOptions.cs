// MongoOptions.cs
// Purpose: Binds the "Mongo" configuration section. ConnectionString is supplied via
// dotnet user-secrets locally or the Mongo__ConnectionString environment variable in
// production; only DatabaseName has a non-secret default in appsettings.json.

namespace smart_solar_mgt_api.Configuration;

public class MongoOptions
{
    public const string SectionName = "Mongo";

    public string ConnectionString { get; set; } = string.Empty;

    public string DatabaseName { get; set; } = string.Empty;
}
