// MicrogridNode.cs
// Purpose: MongoDB document for a solar grid hub (microgrid node). Id is an auto-generated
// ObjectId (unlike Prosumer, a node has no natural-key field to use as _id). Location is stored
// as a native GeoJSON point with a 2dsphere index (see MongoContext.EnsureIndexesAsync) so a
// future "nearby nodes" feature (mobile app spec section 4.3) can use $near/$geoWithin queries
// with no migration; API consumers never see the GeoJSON shape directly (see
// MicrogridNodeResponse, which flattens it to Latitude/Longitude).

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver.GeoJsonObjectModel;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Models.Entities;

public class MicrogridNode
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    public string Name { get; set; } = string.Empty;

    public GeoJsonPoint<GeoJson2DGeographicCoordinates> Location { get; set; } = null!;

    public double CapacityKw { get; set; }

    public List<BatterySlot> BatterySlots { get; set; } = [];

    public List<ScheduleEntry> Schedule { get; set; } = [];

    [BsonRepresentation(BsonType.String)]
    public MicrogridNodeStatus Status { get; set; }

    public DateTime CreatedAt { get; set; }

    public string CreatedBy { get; set; } = "system";

    public DateTime? UpdatedAt { get; set; }

    public string? UpdatedBy { get; set; }
}
