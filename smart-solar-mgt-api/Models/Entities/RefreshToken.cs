// RefreshToken.cs
// Purpose: MongoDB document for an issued refresh token. Only the SHA-256 hash of the raw
// token is ever stored (see SecureTokenGenerator). FamilyId links a rotation chain so token
// reuse (an already-revoked token presented again) can revoke the whole chain at once.
// ExpiresAt carries a TTL index (see MongoContext.EnsureIndexesAsync) for automatic cleanup.
// AccountType discriminates which service UserId resolves against (mirrors Invitation.AccountType)
// - defaults to User on documents written before this field existed, correct since every
// pre-existing refresh token was issued for a staff User (see AuthEndpoints.RefreshAsync).

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Models.Entities;

public class RefreshToken
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    // the User's Id (ObjectId string) or Prosumer's Nic, depending on AccountType
    public string UserId { get; set; } = string.Empty;

    [BsonRepresentation(BsonType.String)]
    public InvitationAccountType AccountType { get; set; } = InvitationAccountType.User;

    public string TokenHash { get; set; } = string.Empty;

    public string FamilyId { get; set; } = string.Empty;

    public DateTime ExpiresAt { get; set; }

    public DateTime CreatedAt { get; set; }

    public DateTime? RevokedAt { get; set; }

    public string? ReplacedByTokenHash { get; set; }
}
