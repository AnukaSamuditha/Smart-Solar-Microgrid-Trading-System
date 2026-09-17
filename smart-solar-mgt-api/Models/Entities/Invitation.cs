// Invitation.cs
// Purpose: MongoDB document for an account-setup invitation. Only the SHA-256 hash of the
// raw token is stored; UsedAt is set atomically on consumption to prevent double-use races
// (see InvitationService.AcceptAsync). ExpiresAt carries a TTL index for automatic cleanup.

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace smart_solar_mgt_api.Models.Entities;

public class Invitation
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    public string UserId { get; set; } = string.Empty;

    public string TokenHash { get; set; } = string.Empty;

    public DateTime ExpiresAt { get; set; }

    public DateTime CreatedAt { get; set; }

    public string CreatedBy { get; set; } = string.Empty;

    public DateTime? UsedAt { get; set; }
}
