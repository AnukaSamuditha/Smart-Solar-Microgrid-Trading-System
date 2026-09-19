// ScheduleEntry.cs
// Purpose: Embedded sub-document representing one day's operating hours on a MicrogridNode's
// weekly schedule. The spec doesn't define a schedule's shape explicitly; this weekly
// open/close-time model is the most natural reading of "updating schedules" for a solar hub's
// trading availability (see docs/microgrid-node-management-implementation-plan.md).

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace smart_solar_mgt_api.Models.Entities;

public class ScheduleEntry
{
    [BsonRepresentation(BsonType.String)]
    public DayOfWeek DayOfWeek { get; set; }

    [BsonRepresentation(BsonType.String)]
    public TimeOnly? OpenTime { get; set; }

    [BsonRepresentation(BsonType.String)]
    public TimeOnly? CloseTime { get; set; }

    public bool IsClosed { get; set; }
}
