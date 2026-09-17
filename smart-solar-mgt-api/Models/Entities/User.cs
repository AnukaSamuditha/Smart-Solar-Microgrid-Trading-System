// User.cs
// Purpose: MongoDB document for a Backoffice or Grid Operator account. PasswordHash is
// null until an invitation is accepted (see InvitationService.AcceptAsync). Email is the
// sole login identifier; Username is a display-only field.

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Models.Entities;

public class User
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    public string Email { get; set; } = string.Empty;

    public string? Username { get; set; }

    public string? PasswordHash { get; set; }

    [BsonRepresentation(BsonType.String)]
    public UserRole Role { get; set; }

    [BsonRepresentation(BsonType.String)]
    public UserStatus Status { get; set; }

    /// <summary>
    /// Marks the account created by SuperAdminSeeder. Used only to block accidental
    /// deactivation of the seeded admin — it is not a distinct role.
    /// </summary>
    public bool IsSeededAdmin { get; set; }

    public DateTime CreatedAt { get; set; }

    public string CreatedBy { get; set; } = "system";

    public DateTime? UpdatedAt { get; set; }

    public string? UpdatedBy { get; set; }

    public DateTime? LastLoginAt { get; set; }
}
