// ProsumerResponse.cs
// Purpose: Public-facing projection of a Prosumer document — omits PasswordHash. Returned by
// the create, register, list, and update endpoints. Phone/Address/RegistrationSource/
// RejectionReason let a Backoffice/Grid Operator reviewer see what a self-registered prosumer
// submitted (see ProsumerService.RegisterAsync) before approving/denying the request.

using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Models.Dtos;

public record ProsumerResponse(
    string Nic,
    string Email,
    string? FullName,
    string? Phone,
    string? Address,
    string Status,
    string RegistrationSource,
    string? RejectionReason,
    DateTime CreatedAt,
    DateTime? UpdatedAt)
{
    // project a Prosumer entity onto its public response shape
    public static ProsumerResponse FromEntity(Prosumer prosumer) =>
        new(
            prosumer.Nic,
            prosumer.Email,
            prosumer.FullName,
            prosumer.Phone,
            prosumer.Address,
            prosumer.Status.ToString(),
            prosumer.RegistrationSource.ToString(),
            prosumer.RejectionReason,
            prosumer.CreatedAt,
            prosumer.UpdatedAt);
}
