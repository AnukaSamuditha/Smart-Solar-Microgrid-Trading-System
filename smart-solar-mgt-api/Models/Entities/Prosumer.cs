// Prosumer.cs
// Purpose: MongoDB document for a solar prosumer profile. Nic is the Mongo _id (the primary
// key, per project-specification.md section 3.2), not a generated ObjectId. PasswordHash is
// null until the prosumer accepts a setup invitation (same invitation-token machinery for both
// creation paths - see InvitationService) and is consumed by POST /api/v1/auth/prosumer/login.
// Phone/Address are collected only on self-registration (see ProsumerService.RegisterAsync),
// so a reviewer approving/denying the request can see what the prosumer submitted; the
// staff-initiated path (ProsumerService.CreateAsync) never sets them.

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Models.Entities;

public class Prosumer
{
    [BsonId]
    [BsonRepresentation(BsonType.String)]
    public string Nic { get; set; } = string.Empty;

    public string Email { get; set; } = string.Empty;

    public string? FullName { get; set; }

    public string? Phone { get; set; }

    public string? Address { get; set; }

    public string? PasswordHash { get; set; }

    [BsonRepresentation(BsonType.String)]
    public ProsumerStatus Status { get; set; }

    [BsonRepresentation(BsonType.String)]
    public ProsumerRegistrationSource RegistrationSource { get; set; } = ProsumerRegistrationSource.StaffInvited;

    // set only when RegistrationSource is SelfRegistered and a reviewer has approved/denied the request
    public string? ReviewedBy { get; set; }

    public DateTime? ReviewedAt { get; set; }

    // set only when Status is Rejected
    public string? RejectionReason { get; set; }

    public DateTime CreatedAt { get; set; }

    public string CreatedBy { get; set; } = "system";

    public DateTime? UpdatedAt { get; set; }

    public string? UpdatedBy { get; set; }
}
