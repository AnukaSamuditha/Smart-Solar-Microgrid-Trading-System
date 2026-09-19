// MicrogridNodeResponse.cs
// Purpose: Public-facing projection of a MicrogridNode document. Flattens the entity's GeoJSON
// Location back into plain Latitude/Longitude fields so API consumers never need to know about
// the driver's internal GeoJSON coordinate ordering ([longitude, latitude]). Returned by the
// create, list, get, and update endpoints.

using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Models.Dtos;

public record MicrogridNodeResponse(
    string Id,
    string Name,
    double Latitude,
    double Longitude,
    double CapacityKw,
    IReadOnlyList<BatterySlotResponse> BatterySlots,
    IReadOnlyList<ScheduleEntryResponse> Schedule,
    string Status,
    DateTime CreatedAt,
    DateTime? UpdatedAt)
{
    // project a MicrogridNode entity onto its public response shape
    public static MicrogridNodeResponse FromEntity(MicrogridNode node) =>
        new(
            node.Id,
            node.Name,
            node.Location.Coordinates.Latitude,
            node.Location.Coordinates.Longitude,
            node.CapacityKw,
            node.BatterySlots.Select(s => new BatterySlotResponse(s.SlotId, s.Status.ToString())).ToList(),
            node.Schedule
                .Select(s => new ScheduleEntryResponse(s.DayOfWeek, s.OpenTime, s.CloseTime, s.IsClosed))
                .ToList(),
            node.Status.ToString(),
            node.CreatedAt,
            node.UpdatedAt);
}

public record BatterySlotResponse(string SlotId, string Status);

public record ScheduleEntryResponse(DayOfWeek DayOfWeek, TimeOnly? OpenTime, TimeOnly? CloseTime, bool IsClosed);
