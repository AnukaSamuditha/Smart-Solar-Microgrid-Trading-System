// BatterySlot.cs
// Purpose: Embedded sub-document representing a single battery storage slot on a MicrogridNode
// (not its own Mongo collection, so it has no [BsonId]). SlotId is assigned sequentially at node
// creation time so a future Reservation Management feature can claim a specific slot by id.

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Models.Entities;

public class BatterySlot
{
    public string SlotId { get; set; } = string.Empty;

    [BsonRepresentation(BsonType.String)]
    public BatterySlotStatus Status { get; set; }
}
