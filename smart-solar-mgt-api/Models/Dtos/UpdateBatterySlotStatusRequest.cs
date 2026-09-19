// UpdateBatterySlotStatusRequest.cs
// Purpose: Request body for PATCH /api/v1/nodes/{id}/battery-slots/{slotId} (Backoffice or Grid
// Operator, per project-specification.md section 6).

using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Models.Dtos;

public record UpdateBatterySlotStatusRequest(BatterySlotStatus Status);
