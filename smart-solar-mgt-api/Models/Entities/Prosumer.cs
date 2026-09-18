// Prosumer.cs
// Purpose: MongoDB document for a solar prosumer profile. Nic is the Mongo _id (the primary
// key, per project-specification.md section 3.2), not a generated ObjectId. PasswordHash is
// null until the prosumer accepts their setup invitation (same invitation-based flow as web
// app Users — see InvitationService) and is stored only for a future mobile-app
// prosumer-authentication feature; no login endpoint uses it yet.

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

    public string? PasswordHash { get; set; }

    [BsonRepresentation(BsonType.String)]
    public ProsumerStatus Status { get; set; }

    public DateTime CreatedAt { get; set; }

    public string CreatedBy { get; set; } = "system";

    public DateTime? UpdatedAt { get; set; }

    public string? UpdatedBy { get; set; }
}
