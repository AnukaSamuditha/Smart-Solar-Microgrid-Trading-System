// MongoContext.cs
// Purpose: Owns the MongoDB client/database handle and exposes typed collection accessors.
// This is the only component in the API that talks to MongoDB directly, consistent with the
// FAT-service architecture in project-specification.md.

using Microsoft.Extensions.Options;
using MongoDB.Driver;
using smart_solar_mgt_api.Configuration;
using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Data;

public class MongoContext
{
    private readonly IMongoDatabase _database;

    // connect to MongoDB using the configured connection string and select the target database
    public MongoContext(IOptions<MongoOptions> options)
    {
        var client = new MongoClient(options.Value.ConnectionString);
        _database = client.GetDatabase(options.Value.DatabaseName);
    }

    public IMongoCollection<User> Users => _database.GetCollection<User>("Users");

    public IMongoCollection<RefreshToken> RefreshTokens => _database.GetCollection<RefreshToken>("RefreshTokens");

    public IMongoCollection<Invitation> Invitations => _database.GetCollection<Invitation>("Invitations");

    public IMongoCollection<Prosumer> Prosumers => _database.GetCollection<Prosumer>("Prosumers");

    public IMongoCollection<MicrogridNode> MicrogridNodes => _database.GetCollection<MicrogridNode>("MicrogridNodes");

    public IMongoCollection<Reservation> Reservations => _database.GetCollection<Reservation>("Reservations");

    // create the unique and TTL indexes required by the auth feature; safe to call on every startup
    public async Task EnsureIndexesAsync(CancellationToken cancellationToken = default)
    {
        var userEmailIndex = new CreateIndexModel<User>(
            Builders<User>.IndexKeys.Ascending(u => u.Email),
            new CreateIndexOptions { Unique = true });
        await Users.Indexes.CreateOneAsync(userEmailIndex, cancellationToken: cancellationToken);

        var refreshTokenHashIndex = new CreateIndexModel<RefreshToken>(
            Builders<RefreshToken>.IndexKeys.Ascending(t => t.TokenHash),
            new CreateIndexOptions { Unique = true });
        await RefreshTokens.Indexes.CreateOneAsync(refreshTokenHashIndex, cancellationToken: cancellationToken);

        var refreshTokenTtlIndex = new CreateIndexModel<RefreshToken>(
            Builders<RefreshToken>.IndexKeys.Ascending(t => t.ExpiresAt),
            new CreateIndexOptions { ExpireAfter = TimeSpan.Zero });
        await RefreshTokens.Indexes.CreateOneAsync(refreshTokenTtlIndex, cancellationToken: cancellationToken);

        var invitationHashIndex = new CreateIndexModel<Invitation>(
            Builders<Invitation>.IndexKeys.Ascending(i => i.TokenHash),
            new CreateIndexOptions { Unique = true });
        await Invitations.Indexes.CreateOneAsync(invitationHashIndex, cancellationToken: cancellationToken);

        var invitationTtlIndex = new CreateIndexModel<Invitation>(
            Builders<Invitation>.IndexKeys.Ascending(i => i.ExpiresAt),
            new CreateIndexOptions { ExpireAfter = TimeSpan.Zero });
        await Invitations.Indexes.CreateOneAsync(invitationTtlIndex, cancellationToken: cancellationToken);

        // Nic is already unique as the Prosumers collection's _id; Email needs its own unique index
        var prosumerEmailIndex = new CreateIndexModel<Prosumer>(
            Builders<Prosumer>.IndexKeys.Ascending(p => p.Email),
            new CreateIndexOptions { Unique = true });
        await Prosumers.Indexes.CreateOneAsync(prosumerEmailIndex, cancellationToken: cancellationToken);

        // 2dsphere index on Location enables $near/$geoWithin queries for a future "nearby
        // nodes" feature (mobile app spec section 4.3) with no migration
        var nodeLocationIndex = new CreateIndexModel<MicrogridNode>(
            Builders<MicrogridNode>.IndexKeys.Geo2DSphere(n => n.Location));
        await MicrogridNodes.Indexes.CreateOneAsync(nodeLocationIndex, cancellationToken: cancellationToken);

        // serves IReservationLookupService.HasActiveReservationsAsync (NodeId + Status + EndTime)
        var reservationNodeStatusIndex = new CreateIndexModel<Reservation>(
            Builders<Reservation>.IndexKeys.Ascending(r => r.NodeId).Ascending(r => r.Status).Ascending(r => r.EndTime));
        await Reservations.Indexes.CreateOneAsync(reservationNodeStatusIndex, cancellationToken: cancellationToken);

        // serves ReservationService's create-time slot-conflict gate check (NodeId + SlotId + Status + EndTime)
        var reservationSlotConflictIndex = new CreateIndexModel<Reservation>(
            Builders<Reservation>.IndexKeys.Ascending(r => r.NodeId).Ascending(r => r.SlotId).Ascending(r => r.Status).Ascending(r => r.EndTime));
        await Reservations.Indexes.CreateOneAsync(reservationSlotConflictIndex, cancellationToken: cancellationToken);

        // serves the reservation list endpoint's prosumerNic filter
        var reservationProsumerIndex = new CreateIndexModel<Reservation>(
            Builders<Reservation>.IndexKeys.Ascending(r => r.ProsumerNic));
        await Reservations.Indexes.CreateOneAsync(reservationProsumerIndex, cancellationToken: cancellationToken);

        // serves DashboardService's recent-activity feed (sort by CreatedAt desc)
        var reservationCreatedAtIndex = new CreateIndexModel<Reservation>(
            Builders<Reservation>.IndexKeys.Descending(r => r.CreatedAt));
        await Reservations.Indexes.CreateOneAsync(reservationCreatedAtIndex, cancellationToken: cancellationToken);

        // serves DashboardService's recent-activity feed (sort by CreatedAt desc)
        var nodeCreatedAtIndex = new CreateIndexModel<MicrogridNode>(
            Builders<MicrogridNode>.IndexKeys.Descending(n => n.CreatedAt));
        await MicrogridNodes.Indexes.CreateOneAsync(nodeCreatedAtIndex, cancellationToken: cancellationToken);

        // serves DashboardService's recent-activity feed (sort by CreatedAt desc)
        var prosumerCreatedAtIndex = new CreateIndexModel<Prosumer>(
            Builders<Prosumer>.IndexKeys.Descending(p => p.CreatedAt));
        await Prosumers.Indexes.CreateOneAsync(prosumerCreatedAtIndex, cancellationToken: cancellationToken);
    }
}
