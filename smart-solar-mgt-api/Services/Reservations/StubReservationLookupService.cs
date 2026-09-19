// StubReservationLookupService.cs
// Purpose: Placeholder IReservationLookupService implementation, registered in Program.cs until
// Energy Slot Reservation Management (project-specification.md section 3.4) is built. Always
// reports no active reservations, so node deactivation is never blocked by this check today.

namespace smart_solar_mgt_api.Services.Reservations;

public class StubReservationLookupService : IReservationLookupService
{
    // TODO: replace with a real lookup against the Reservations collection once Energy Slot
    // Reservation Management (project-specification.md section 3.4) is implemented.
    public Task<bool> HasActiveReservationsAsync(string nodeId, CancellationToken cancellationToken = default) =>
        Task.FromResult(false);
}
