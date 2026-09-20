// DashboardEndpoints.cs
// Purpose: Maps read-only dashboard/insights endpoints that summarize platform status for the
// backoffice web app's landing page. Available to both Backoffice and Grid Operator staff (the
// "DashboardAccess" policy) — the frontend's Backoffice-only Staff Overview widget instead reuses
// the existing, separately-authorized GET /api/v1/users/ endpoint rather than this group growing
// a role-aware section. See smart-solar-mgt-fe/docs/dashboard-insights-implementation-plan.md.

using smart_solar_mgt_api.Services.Dashboard;

namespace smart_solar_mgt_api.Endpoints;

public static class DashboardEndpoints
{
    private const string DashboardAccessPolicy = "DashboardAccess";

    // register the /api/v1/dashboard endpoint group
    public static IEndpointRouteBuilder MapDashboardEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/v1/dashboard").WithTags("Dashboard");

        group.MapGet("/summary", GetSummaryAsync).RequireAuthorization(DashboardAccessPolicy);
        group.MapGet("/recent-activity", GetRecentActivityAsync).RequireAuthorization(DashboardAccessPolicy);

        return app;
    }

    // aggregated node/battery-slot/reservation/prosumer counts for the KPI strip and donut charts
    private static async Task<IResult> GetSummaryAsync(
        IDashboardService dashboardService,
        CancellationToken cancellationToken)
    {
        var summary = await dashboardService.GetSummaryAsync(cancellationToken);
        return Results.Ok(summary);
    }

    // merged, most-recent-first feed of node/prosumer/reservation creation events
    private static async Task<IResult> GetRecentActivityAsync(
        IDashboardService dashboardService,
        CancellationToken cancellationToken,
        int limit = 10)
    {
        var activity = await dashboardService.GetRecentActivityAsync(limit, cancellationToken);
        return Results.Ok(activity);
    }
}
