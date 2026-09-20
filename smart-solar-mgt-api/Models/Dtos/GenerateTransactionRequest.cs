// GenerateTransactionRequest.cs
// Purpose: Request body for POST /api/v1/transactions/generate - a prosumer requesting a QR
// Energy Transfer Pass for one of their own Confirmed reservations.

namespace smart_solar_mgt_api.Models.Dtos;

public record GenerateTransactionRequest(string ReservationId);
