// RefreshToken.cs
// Purpose: MongoDB document for an issued refresh token. Only the SHA-256 hash of the raw
// token is ever stored (see SecureTokenGenerator). FamilyId links a rotation chain so token
// reuse (an already-revoked token presented again) can revoke the whole chain at once.
// ExpiresAt carries a TTL index (see MongoContext.EnsureIndexesAsync) for automatic cleanup.

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace smart_solar_mgt_api.Models.Entities;

public class RefreshToken
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    public string UserId { get; set; } = string.Empty;

    public string TokenHash { get; set; } = string.Empty;

    public string FamilyId { get; set; } = string.Empty;

    public DateTime ExpiresAt { get; set; }

    public DateTime CreatedAt { get; set; }

    public DateTime? RevokedAt { get; set; }

    public string? ReplacedByTokenHash { get; set; }
}
