// IReservationLookupService.cs
// Purpose: Seam between Microgrid Node Management and the not-yet-built Energy Slot Reservation
// Management feature (project-specification.md section 3.4). MicrogridNodeService.DeactivateAsync
// calls this before flipping a node's status, so the "deactivation blocked while active
// reservations exist" rule can be wired to a real implementation later with no change to Node
// Management's own code — only the DI registration in Program.cs changes.
// See StubReservationLookupService for today's placeholder implementation.

namespace smart_solar_mgt_api.Services.Reservations;

public interface IReservationLookupService
{
    Task<bool> HasActiveReservationsAsync(string nodeId, CancellationToken cancellationToken = default);
}
