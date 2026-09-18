// ProsumerResponse.cs
// Purpose: Public-facing projection of a Prosumer document — omits PasswordHash. Returned by
// the create, list, and update endpoints.

using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Models.Dtos;

public record ProsumerResponse(
    string Nic,
    string Email,
    string? FullName,
    string Status,
    DateTime CreatedAt,
    DateTime? UpdatedAt)
{
    // project a Prosumer entity onto its public response shape
    public static ProsumerResponse FromEntity(Prosumer prosumer) =>
        new(
            prosumer.Nic,
            prosumer.Email,
            prosumer.FullName,
            prosumer.Status.ToString(),
            prosumer.CreatedAt,
            prosumer.UpdatedAt);
}
