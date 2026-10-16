// UpdateMyProfileRequest.cs
// Purpose: Request body for PUT /api/v1/prosumers/me - a prosumer editing their own contact
// details from the mobile app. Email is deliberately absent (it is the prosumer's login
// identifier and changing it needs a verification flow that doesn't exist yet), as is NIC (the
// primary key). FullName/Phone/Address are all required, matching the mobile app's own form rules.

namespace smart_solar_mgt_api.Models.Dtos;

public record UpdateMyProfileRequest(string? FullName, string? Phone, string? Address);
