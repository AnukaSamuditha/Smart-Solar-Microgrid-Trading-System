// TransactionResponse.cs
// Purpose: Response for POST /api/v1/transactions/generate. Token is the raw, one-time QR
// payload - only Transaction.TokenHash is ever persisted (see Models/Entities/Transaction.cs),
// so this is the only response shape that ever carries the raw value; the mobile app renders it
// as the QR code shown to the prosumer and never needs to ask the server for it again by id.

using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Models.Dtos;

public record TransactionResponse(
    string Id,
    string ReservationId,
    string Token,
    string Status,
    DateTime GeneratedAt,
    DateTime ExpiresAt)
{
    // project a just-generated Transaction entity plus its one-time raw token onto the response shape
    public static TransactionResponse FromEntity(Transaction transaction, string rawToken) =>
        new(
            transaction.Id,
            transaction.ReservationId,
            rawToken,
            transaction.Status.ToString(),
            transaction.GeneratedAt,
            transaction.ExpiresAt);
}
