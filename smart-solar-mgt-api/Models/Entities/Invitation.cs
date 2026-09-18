// Invitation.cs
// Purpose: MongoDB document for an account-setup invitation, shared by both web app Users and
// Prosumer profiles (see AccountType). Only the SHA-256 hash of the raw token is stored; UsedAt
// is set atomically on consumption to prevent double-use races (see InvitationService.AcceptAsync).
// ExpiresAt carries a TTL index for automatic cleanup.

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Models.Entities;

public class Invitation
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    // the invited User's Id (ObjectId string) or Prosumer's Nic, depending on AccountType
    public string AccountId { get; set; } = string.Empty;

    [BsonRepresentation(BsonType.String)]
    public InvitationAccountType AccountType { get; set; } = InvitationAccountType.User;

    public string TokenHash { get; set; } = string.Empty;

    public DateTime ExpiresAt { get; set; }

    public DateTime CreatedAt { get; set; }

    public string CreatedBy { get; set; } = string.Empty;

    public DateTime? UsedAt { get; set; }
}
