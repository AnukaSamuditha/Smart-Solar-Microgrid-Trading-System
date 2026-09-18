// AcceptInvitationResponse.cs
// Purpose: Response body for POST /api/v1/auth/accept-invitation. AccountType tells the
// frontend which success UI to show — Users are told they can sign in to the web dashboard,
// Prosumers are told to use the mobile app instead (the web dashboard is Backoffice/Grid
// Operator only, per project-specification.md section 3).

namespace smart_solar_mgt_api.Models.Dtos;

public record AcceptInvitationResponse(string AccountType);
