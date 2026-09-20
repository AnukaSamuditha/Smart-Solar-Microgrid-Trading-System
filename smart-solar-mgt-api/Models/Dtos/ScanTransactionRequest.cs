// ScanTransactionRequest.cs
// Purpose: Request body for POST /api/v1/transactions/scan - the raw token a Grid Operator's
// device just scanned off a prosumer's QR Energy Transfer Pass.

namespace smart_solar_mgt_api.Models.Dtos;

public record ScanTransactionRequest(string Token);
