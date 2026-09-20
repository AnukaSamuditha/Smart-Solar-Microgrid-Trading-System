// TransactionEndpoints.cs
// Purpose: Maps QR Energy Transfer Pass endpoints (project-specification.md sections 4.2/4.4).
// A prosumer generates a pass for their own Confirmed reservation (RoleNames.Prosumer); a Grid
// Operator scans it then, as a separate explicit step, completes it (the
// "TransactionFinalization" policy, Grid-Operator-only - scanning/finalizing is a field/
// operational action, unlike every other staff policy in this codebase which also allows
// Backoffice). See Services/Transactions/TransactionService.cs for the full design rationale.

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using smart_solar_mgt_api.Authorization;
using smart_solar_mgt_api.Models.Dtos;
using smart_solar_mgt_api.Services.Transactions;

namespace smart_solar_mgt_api.Endpoints;

public static class TransactionEndpoints
{
    private const string TransactionFinalizationPolicy = "TransactionFinalization";

    // register the /api/v1/transactions endpoint group
    public static IEndpointRouteBuilder MapTransactionEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/v1/transactions").WithTags("Transactions");

        group.MapPost("/generate", GenerateTransactionAsync).RequireAuthorization(RoleNames.Prosumer);
        group.MapPost("/scan", ScanTransactionAsync).RequireAuthorization(TransactionFinalizationPolicy);
        group.MapPatch("/{id}/complete", CompleteTransactionAsync).RequireAuthorization(TransactionFinalizationPolicy);

        return app;
    }

    // prosumer self-service: generate a QR Energy Transfer Pass for one of the caller's own
    // Confirmed reservations; the response's Token is the only place the raw value is ever returned
    private static async Task<IResult> GenerateTransactionAsync(
        GenerateTransactionRequest request,
        ClaimsPrincipal principal,
        ITransactionService transactionService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.ReservationId))
        {
            return Results.BadRequest(new { error = "ReservationIdRequired" });
        }

        var nic = principal.FindFirstValue(JwtRegisteredClaimNames.Sub);
        if (string.IsNullOrWhiteSpace(nic))
        {
            return Results.Unauthorized();
        }

        var (result, transaction, rawToken) = await transactionService.GenerateAsync(request.ReservationId, nic, cancellationToken);
        if (result != GenerateTransactionResult.Succeeded || transaction is null || rawToken is null)
        {
            return MapGenerateResult(result);
        }

        return Results.Ok(TransactionResponse.FromEntity(transaction, rawToken));
    }

    // Grid Operator scans a pass: read-only verification, returns display-ready details for a
    // confirmation screen before the separate explicit Complete step below
    private static async Task<IResult> ScanTransactionAsync(
        ScanTransactionRequest request,
        ClaimsPrincipal principal,
        ITransactionService transactionService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Token))
        {
            return Results.BadRequest(new { error = "TokenRequired" });
        }

        var operatorUserId = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var (result, transaction) = await transactionService.ScanAsync(request.Token, operatorUserId, cancellationToken);
        if (result != ScanTransactionResult.Succeeded || transaction is null)
        {
            return MapScanResult(result);
        }

        return Results.Ok(TransactionVerificationResponse.FromEnriched(transaction));
    }

    // Grid Operator finalizes a previously-scanned pass: the physical energy transfer is done, so
    // the reservation moves to Completed and the node's slot is released back to Available
    private static async Task<IResult> CompleteTransactionAsync(
        string id,
        ClaimsPrincipal principal,
        ITransactionService transactionService,
        CancellationToken cancellationToken)
    {
        var operatorUserId = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var (result, transaction) = await transactionService.CompleteAsync(id, operatorUserId, cancellationToken);
        if (result != CompleteTransactionResult.Succeeded || transaction is null)
        {
            return MapCompleteResult(result);
        }

        return Results.Ok(TransactionVerificationResponse.FromEnriched(transaction));
    }

    // translate a GenerateTransactionResult into the matching HTTP response; NotOwner is masked
    // as the same ReservationNotFound response, same ownership-hiding precedent as
    // ReservationService's prosumer-facing methods
    private static IResult MapGenerateResult(GenerateTransactionResult result) => result switch
    {
        GenerateTransactionResult.ReservationNotFound => Results.NotFound(new { error = "ReservationNotFound" }),
        GenerateTransactionResult.NotOwner => Results.NotFound(new { error = "ReservationNotFound" }),
        GenerateTransactionResult.ReservationNotConfirmed => Results.Conflict(new { error = "ReservationNotConfirmed" }),
        GenerateTransactionResult.ReservationWindowElapsed => Results.Conflict(new { error = "ReservationWindowElapsed" }),
        _ => Results.Problem()
    };

    // translate a ScanTransactionResult into the matching HTTP response
    private static IResult MapScanResult(ScanTransactionResult result) => result switch
    {
        ScanTransactionResult.NotFound => Results.NotFound(new { error = "NotFound" }),
        ScanTransactionResult.AlreadyUsed => Results.Conflict(new { error = "AlreadyUsed" }),
        ScanTransactionResult.Expired => Results.Conflict(new { error = "Expired" }),
        ScanTransactionResult.ReservationNoLongerConfirmed => Results.Conflict(new { error = "ReservationNoLongerConfirmed" }),
        _ => Results.Problem()
    };

    // translate a CompleteTransactionResult into the matching HTTP response
    private static IResult MapCompleteResult(CompleteTransactionResult result) => result switch
    {
        CompleteTransactionResult.NotFound => Results.NotFound(new { error = "NotFound" }),
        CompleteTransactionResult.NotScanned => Results.Conflict(new { error = "NotScanned" }),
        _ => Results.Problem()
    };
}
