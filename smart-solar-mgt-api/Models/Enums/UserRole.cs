// UserRole.cs
// Purpose: The staff roles supported by this feature, matching the vocabulary used in
// project-specification.md. The seeded super-admin account is a Backoffice user (see
// SuperAdminSeeder) rather than a separate role, since the spec does not define one.

namespace smart_solar_mgt_api.Models.Enums;

public enum UserRole
{
    Backoffice,
    GridOperator
}
