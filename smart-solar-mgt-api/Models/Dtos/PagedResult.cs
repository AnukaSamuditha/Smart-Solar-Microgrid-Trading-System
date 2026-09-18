// PagedResult.cs
// Purpose: Generic paginated response envelope. First paginated response shape in this
// codebase (the existing Users list is intentionally unpaginated) — reusable by future
// list endpoints (e.g. Grid Nodes, Reservations) that need the same shape.

namespace smart_solar_mgt_api.Models.Dtos;

public record PagedResult<T>(IReadOnlyList<T> Items, long TotalCount, int Page, int PageSize);
