// RefreshRequest.cs
// Purpose: Request body for POST /api/v1/auth/refresh and POST /api/v1/auth/logout — both
// operate on a presented raw refresh token. RefreshToken is optional here because the web
// client sends none (it relies on the HttpOnly refresh_token cookie instead); only the
// mobile/bearer client populates it. See Endpoints/AuthEndpoints.cs for the cookie fallback.

namespace smart_solar_mgt_api.Models.Dtos;

public record RefreshRequest(string? RefreshToken = null);
