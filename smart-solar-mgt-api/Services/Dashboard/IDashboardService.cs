// IDashboardService.cs
// Purpose: Contract for the dashboard aggregation service consumed by DashboardEndpoints.

using smart_solar_mgt_api.Models.Dtos;

namespace smart_solar_mgt_api.Services.Dashboard;

public interface IDashboardService
{
    Task<DashboardSummaryResponse> GetSummaryAsync(CancellationToken cancellationToken = default);

    Task<RecentActivityResponse> GetRecentActivityAsync(int limit = 10, CancellationToken cancellationToken = default);
}
