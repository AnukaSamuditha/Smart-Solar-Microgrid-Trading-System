// ReservationLookupService.cs
// Purpose: Real implementation of the IReservationLookupService seam, replacing
// StubReservationLookupService now that Energy Slot Reservation Management (project-specification.md
// section 3.4) exists. MicrogridNodeService.DeactivateAsync depends on this; no other Node
// Management code changes as a result of swapping the DI registration in Program.cs.

using MongoDB.Driver;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Reservations;

public class ReservationLookupService : IReservationLookupService
{
    private readonly MongoContext _mongoContext;

    public ReservationLookupService(MongoContext mongoContext)
    {
        _mongoContext = mongoContext;
    }

    // report whether a node has any Confirmed reservation whose slot window hasn't elapsed yet;
    // no StartTime condition, so a node stays blocked from deactivation while it holds any future
    // confirmed reservation, not just one already in progress
    public async Task<bool> HasActiveReservationsAsync(string nodeId, CancellationToken cancellationToken = default) =>
        await _mongoContext.Reservations
            .Find(r => r.NodeId == nodeId && r.Status == ReservationStatus.Confirmed && r.EndTime > DateTime.UtcNow)
            .AnyAsync(cancellationToken);
}
