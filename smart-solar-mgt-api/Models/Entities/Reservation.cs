// Reservation.cs
// Purpose: MongoDB document for an energy slot reservation (spec section 3.4). References a
// Prosumer (by NIC) and a specific MicrogridNode battery slot (by NodeId + SlotId) with plain,
// unvalidated foreign-key strings, matching existing precedent (RefreshToken.UserId,
// Invitation.AccountId). Id is an auto-generated ObjectId — a reservation has no natural key.

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Models.Entities;

public class Reservation
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    public string ProsumerNic { get; set; } = string.Empty;

    public string NodeId { get; set; } = string.Empty;

    public string SlotId { get; set; } = string.Empty;

    public DateTime StartTime { get; set; }

    public DateTime EndTime { get; set; }

    [BsonRepresentation(BsonType.String)]
    public ReservationStatus Status { get; set; }

    public DateTime CreatedAt { get; set; }

    public string CreatedBy { get; set; } = "system";

    public DateTime? UpdatedAt { get; set; }

    public string? UpdatedBy { get; set; }
}
