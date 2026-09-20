// Transaction.cs
// Purpose: MongoDB document for a QR "Energy Transfer Pass" - the server-verified handshake a
// Grid Operator uses to redeem a prosumer's Confirmed reservation at the physical node (spec
// sections 4.2/4.4), replacing the mobile app's original on-device HMAC-signed pass. Only
// TokenHash is persisted (SHA-256, via the same Services.Auth.SecureTokenGenerator already used
// for refresh/invitation tokens) - the raw token is returned once, in
// TransactionService.GenerateAsync's response, and never stored at rest, exactly like
// Invitation.TokenHash. Deliberately references its Reservation/Prosumer/Node by plain,
// unvalidated id strings, matching Reservation's own precedent. Id is an auto-generated
// ObjectId - a transaction has no natural key.

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Models.Entities;

public class Transaction
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    public string ReservationId { get; set; } = string.Empty;

    public string ProsumerNic { get; set; } = string.Empty;

    public string NodeId { get; set; } = string.Empty;

    public string SlotId { get; set; } = string.Empty;

    public string TokenHash { get; set; } = string.Empty;

    [BsonRepresentation(BsonType.String)]
    public TransactionStatus Status { get; set; }

    public DateTime GeneratedAt { get; set; }

    public DateTime ExpiresAt { get; set; }

    public string? ScannedByUserId { get; set; }

    public DateTime? ScannedAt { get; set; }

    public string? CompletedByUserId { get; set; }

    public DateTime? CompletedAt { get; set; }
}
