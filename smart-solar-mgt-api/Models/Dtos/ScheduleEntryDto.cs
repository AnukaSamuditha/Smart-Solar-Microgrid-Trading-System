// ScheduleEntryDto.cs
// Purpose: Wire format for one day's operating hours, used in UpdateMicrogridNodeScheduleRequest
// and mirrored back in MicrogridNodeResponse as ScheduleEntryResponse.

namespace smart_solar_mgt_api.Models.Dtos;

public record ScheduleEntryDto(DayOfWeek DayOfWeek, TimeOnly? OpenTime, TimeOnly? CloseTime, bool IsClosed);
