// CreateMicrogridNodeRequest.cs
// Purpose: Request body for POST /api/v1/nodes (Backoffice-only). Latitude/Longitude are plain
// doubles at the API boundary; MicrogridNodeService converts them to/from the entity's GeoJSON
// Location field. BatterySlotCount seeds that many Available slots at creation time.
// OperatingStartTime/OperatingEndTime seed the node's initial weekly Schedule with the same
// daily window on every day of the week; the Node Schedules page can refine individual days
// afterward via PATCH /{id}/schedule.

namespace smart_solar_mgt_api.Models.Dtos;

public record CreateMicrogridNodeRequest(
    string Name,
    double Latitude,
    double Longitude,
    double CapacityKw,
    int BatterySlotCount,
    TimeOnly OperatingStartTime,
    TimeOnly OperatingEndTime);
