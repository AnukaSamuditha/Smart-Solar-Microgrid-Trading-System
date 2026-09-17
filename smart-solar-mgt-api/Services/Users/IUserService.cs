// IUserService.cs
// Purpose: Abstraction over user account queries, profile updates, and lifecycle management
// (deactivate/reactivate/delete). Implemented by UserService.

using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Users;

public interface IUserService
{
    Task<User> CreateInvitedUserAsync(
        string email,
        string? username,
        UserRole role,
        string createdByUserId,
        CancellationToken cancellationToken = default);

    Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default);

    Task<User?> GetByIdAsync(string userId, CancellationToken cancellationToken = default);

    Task<IReadOnlyList<User>> ListAsync(CancellationToken cancellationToken = default);

    Task<UserActionResult> DeactivateAsync(string userId, string performedByUserId, CancellationToken cancellationToken = default);

    Task<UserActionResult> ReactivateAsync(string userId, string performedByUserId, CancellationToken cancellationToken = default);

    Task<UserActionResult> UpdateAsync(
        string userId,
        string email,
        string? username,
        string performedByUserId,
        CancellationToken cancellationToken = default);

    Task<UserActionResult> DeleteAsync(string userId, string performedByUserId, CancellationToken cancellationToken = default);

    Task RecordLoginAsync(string userId, CancellationToken cancellationToken = default);

    Task SendPasswordSetConfirmationEmailAsync(User user, CancellationToken cancellationToken = default);
}

public enum UserActionResult
{
    Succeeded,
    NotFound,
    Protected,
    EmailConflict
}
