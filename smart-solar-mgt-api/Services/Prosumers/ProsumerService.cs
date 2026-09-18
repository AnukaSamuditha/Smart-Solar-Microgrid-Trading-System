// ProsumerService.cs
// Purpose: Orchestrates prosumer profile creation (invitation-based, like web app Users — see
// UserService), search/pagination, updates, and lifecycle transitions (deactivate/reactivate).
// NIC is the Mongo _id and is never mutated after creation.

using System.Linq.Expressions;
using System.Text.RegularExpressions;
using Microsoft.Extensions.Options;
using MongoDB.Bson;
using MongoDB.Driver;
using smart_solar_mgt_api.Configuration;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Auth;
using smart_solar_mgt_api.Services.Email;

namespace smart_solar_mgt_api.Services.Prosumers;

public class ProsumerService : IProsumerService
{
    private const int MaxPageSize = 100;

    private readonly MongoContext _mongoContext;
    private readonly IInvitationService _invitationService;
    private readonly IEmailSender _emailSender;
    private readonly InvitationOptions _invitationOptions;

    public ProsumerService(
        MongoContext mongoContext,
        IInvitationService invitationService,
        IEmailSender emailSender,
        IOptions<InvitationOptions> invitationOptions)
    {
        _mongoContext = mongoContext;
        _invitationService = invitationService;
        _emailSender = emailSender;
        _invitationOptions = invitationOptions.Value;
    }

    // create a new Invited prosumer profile (no password yet), then generate a setup invitation
    // and email it, exactly like UserService.CreateInvitedUserAsync; maps duplicate-key errors
    // on the NIC (_id) or the unique Email index to the matching conflict result
    public async Task<ProsumerCreateResult> CreateAsync(
        string nic,
        string email,
        string? fullName,
        string createdByUserId,
        CancellationToken cancellationToken = default)
    {
        var prosumer = new Prosumer
        {
            Nic = NormalizeNic(nic),
            Email = NormalizeEmail(email),
            FullName = string.IsNullOrWhiteSpace(fullName) ? null : fullName.Trim(),
            Status = ProsumerStatus.Invited,
            CreatedAt = DateTime.UtcNow,
            CreatedBy = createdByUserId
        };

        try
        {
            await _mongoContext.Prosumers.InsertOneAsync(prosumer, cancellationToken: cancellationToken);
        }
        catch (MongoWriteException ex) when (ex.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            // the duplicate-key error doesn't distinguish which unique constraint tripped, so
            // check whether the NIC already exists to tell a NIC clash apart from an email clash
            var nicTaken = await GetByNicAsync(prosumer.Nic, cancellationToken) is not null;
            return nicTaken ? ProsumerCreateResult.NicConflict : ProsumerCreateResult.EmailConflict;
        }

        var rawToken = await _invitationService.CreateInvitationAsync(
            prosumer.Nic, createdByUserId, InvitationAccountType.Prosumer, cancellationToken);
        await SendInvitationEmailAsync(prosumer, rawToken, cancellationToken);

        return ProsumerCreateResult.Succeeded;
    }

    // look up a prosumer by NIC, used by update/deactivate/reactivate and the get-by-id path
    public async Task<Prosumer?> GetByNicAsync(string nic, CancellationToken cancellationToken = default) =>
        await _mongoContext.Prosumers.Find(p => p.Nic == NormalizeNic(nic)).FirstOrDefaultAsync(cancellationToken);

    // look up a prosumer by normalized email, used during login to detect a prosumer trying to
    // sign in to the web dashboard (which is Backoffice/Grid Operator only — see AuthEndpoints)
    public async Task<Prosumer?> GetByEmailAsync(string email, CancellationToken cancellationToken = default)
    {
        var normalizedEmail = NormalizeEmail(email);
        return await _mongoContext.Prosumers.Find(p => p.Email == normalizedEmail).FirstOrDefaultAsync(cancellationToken);
    }

    // search/filter/sort/paginate prosumer profiles for the admin listing endpoint
    public async Task<(IReadOnlyList<Prosumer> Items, long TotalCount)> ListAsync(
        string? search,
        ProsumerStatus? status,
        int page,
        int pageSize,
        string? sortBy,
        string? sortDir,
        CancellationToken cancellationToken = default)
    {
        var normalizedPage = Math.Max(page, 1);
        var normalizedPageSize = Math.Clamp(pageSize <= 0 ? 20 : pageSize, 1, MaxPageSize);

        var filterBuilder = Builders<Prosumer>.Filter;
        var filters = new List<FilterDefinition<Prosumer>>();

        if (!string.IsNullOrWhiteSpace(search))
        {
            var pattern = new BsonRegularExpression(Regex.Escape(search.Trim()), "i");
            filters.Add(filterBuilder.Or(
                filterBuilder.Regex(p => p.Nic, pattern),
                filterBuilder.Regex(p => p.Email, pattern),
                filterBuilder.Regex(p => p.FullName, pattern)));
        }

        if (status is not null)
        {
            filters.Add(filterBuilder.Eq(p => p.Status, status.Value));
        }

        var filter = filters.Count == 0 ? FilterDefinition<Prosumer>.Empty : filterBuilder.And(filters);

        var sort = BuildSort(sortBy, sortDir);

        var totalCount = await _mongoContext.Prosumers.CountDocumentsAsync(filter, cancellationToken: cancellationToken);
        var items = await _mongoContext.Prosumers.Find(filter)
            .Sort(sort)
            .Skip((normalizedPage - 1) * normalizedPageSize)
            .Limit(normalizedPageSize)
            .ToListAsync(cancellationToken);

        return (items, totalCount);
    }

    // update a prosumer's email/full name; refuses if the new email is already taken by another profile
    public async Task<ProsumerActionResult> UpdateAsync(
        string nic,
        string email,
        string? fullName,
        string performedByUserId,
        CancellationToken cancellationToken = default)
    {
        var prosumer = await GetByNicAsync(nic, cancellationToken);
        if (prosumer is null)
        {
            return ProsumerActionResult.NotFound;
        }

        var filter = Builders<Prosumer>.Filter.Eq(p => p.Nic, prosumer.Nic);
        var update = Builders<Prosumer>.Update
            .Set(p => p.Email, NormalizeEmail(email))
            .Set(p => p.FullName, string.IsNullOrWhiteSpace(fullName) ? null : fullName.Trim())
            .Set(p => p.UpdatedAt, DateTime.UtcNow)
            .Set(p => p.UpdatedBy, performedByUserId);

        try
        {
            await _mongoContext.Prosumers.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);
        }
        catch (MongoWriteException ex) when (ex.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            return ProsumerActionResult.EmailConflict;
        }

        return ProsumerActionResult.Succeeded;
    }

    // deactivate a prosumer profile, blocking it from future mobile-app authentication
    public async Task<ProsumerActionResult> DeactivateAsync(string nic, string performedByUserId, CancellationToken cancellationToken = default) =>
        await SetStatusAsync(nic, ProsumerStatus.Deactivated, performedByUserId, cancellationToken);

    // reactivate a previously deactivated prosumer profile (Backoffice-only, enforced at the endpoint)
    public async Task<ProsumerActionResult> ReactivateAsync(string nic, string performedByUserId, CancellationToken cancellationToken = default) =>
        await SetStatusAsync(nic, ProsumerStatus.Active, performedByUserId, cancellationToken);

    // shared lifecycle-transition logic used by DeactivateAsync and ReactivateAsync
    private async Task<ProsumerActionResult> SetStatusAsync(
        string nic,
        ProsumerStatus newStatus,
        string performedByUserId,
        CancellationToken cancellationToken)
    {
        var prosumer = await GetByNicAsync(nic, cancellationToken);
        if (prosumer is null)
        {
            return ProsumerActionResult.NotFound;
        }

        var filter = Builders<Prosumer>.Filter.Eq(p => p.Nic, prosumer.Nic);
        var update = Builders<Prosumer>.Update
            .Set(p => p.Status, newStatus)
            .Set(p => p.UpdatedAt, DateTime.UtcNow)
            .Set(p => p.UpdatedBy, performedByUserId);
        await _mongoContext.Prosumers.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);

        return ProsumerActionResult.Succeeded;
    }

    // translate the requested sort field/direction into a Mongo sort definition, defaulting to newest-first
    private static SortDefinition<Prosumer> BuildSort(string? sortBy, string? sortDir)
    {
        var descending = !string.Equals(sortDir, "asc", StringComparison.OrdinalIgnoreCase);

        Expression<Func<Prosumer, object>> field = sortBy?.ToLowerInvariant() switch
        {
            "nic" => p => p.Nic,
            "email" => p => p.Email,
            _ => p => p.CreatedAt
        };

        return descending
            ? Builders<Prosumer>.Sort.Descending(field)
            : Builders<Prosumer>.Sort.Ascending(field);
    }

    // trim and uppercase a NIC so lookups/uniqueness are case-insensitive on the V/X suffix
    private static string NormalizeNic(string nic) => nic.Trim().ToUpperInvariant();

    // trim and lowercase an email so lookups/uniqueness are case-insensitive
    private static string NormalizeEmail(string email) => email.Trim().ToLowerInvariant();

    // build and send the invitation email containing the one-time setup link
    private async Task SendInvitationEmailAsync(Prosumer prosumer, string rawToken, CancellationToken cancellationToken)
    {
        var inviteLink = $"{_invitationOptions.FrontendBaseUrl}/reset-password?token={Uri.EscapeDataString(rawToken)}";
        var (subject, body) = ProsumerInvitationEmailTemplate.Build(prosumer, inviteLink, _invitationOptions.TokenLifetimeHours);

        await _emailSender.SendAsync(prosumer.Email, subject, body, cancellationToken);
    }

    // notify a prosumer that their password was just set/updated, after a successful invitation-accept
    public async Task SendPasswordSetConfirmationEmailAsync(Prosumer prosumer, CancellationToken cancellationToken = default)
    {
        var (subject, body) = ProsumerPasswordSetConfirmationEmailTemplate.Build(prosumer);
        await _emailSender.SendAsync(prosumer.Email, subject, body, cancellationToken);
    }
}
