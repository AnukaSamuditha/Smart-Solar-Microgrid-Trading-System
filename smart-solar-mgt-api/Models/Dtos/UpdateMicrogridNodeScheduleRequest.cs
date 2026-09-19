// UpdateMicrogridNodeScheduleRequest.cs
// Purpose: Request body for PATCH /api/v1/nodes/{id}/schedule (Backoffice-only). Replaces the
// node's entire weekly schedule with the supplied list of day entries.

namespace smart_solar_mgt_api.Models.Dtos;

public record UpdateMicrogridNodeScheduleRequest(List<ScheduleEntryDto> Schedule);
