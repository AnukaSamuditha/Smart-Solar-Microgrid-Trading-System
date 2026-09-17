// AcceptInvitationRequest.cs
// Purpose: Request body for POST /api/v1/auth/accept-invitation.

namespace smart_solar_mgt_api.Models.Dtos;

public record AcceptInvitationRequest(string Token, string NewPassword);
