// IProsumerService.cs
// Purpose: Abstraction over prosumer profile creation (invitation-based, like web app Users),
// search/pagination, updates, and lifecycle management (deactivate/reactivate). Implemented by
// ProsumerService.

using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Prosumers;

public interface IProsumerService
{
    Task<ProsumerCreateResult> CreateAsync(
        string nic,
        string email,
        string? fullName,
        string createdByUserId,
        CancellationToken cancellationToken = default);

    Task<Prosumer?> GetByNicAsync(string nic, CancellationToken cancellationToken = default);

    Task<Prosumer?> GetByEmailAsync(string email, CancellationToken cancellationToken = default);

    Task<(IReadOnlyList<Prosumer> Items, long TotalCount)> ListAsync(
        string? search,
        ProsumerStatus? status,
        int page,
        int pageSize,
        string? sortBy,
        string? sortDir,
        CancellationToken cancellationToken = default);

    Task<ProsumerActionResult> UpdateAsync(
        string nic,
        string email,
        string? fullName,
        string performedByUserId,
        CancellationToken cancellationToken = default);

    Task<ProsumerActionResult> DeactivateAsync(string nic, string performedByUserId, CancellationToken cancellationToken = default);

    Task<ProsumerActionResult> ReactivateAsync(string nic, string performedByUserId, CancellationToken cancellationToken = default);

    Task SendPasswordSetConfirmationEmailAsync(Prosumer prosumer, CancellationToken cancellationToken = default);
}

public enum ProsumerActionResult
{
    Succeeded,
    NotFound,
    EmailConflict
}

public enum ProsumerCreateResult
{
    Succeeded,
    NicConflict,
    EmailConflict
}
