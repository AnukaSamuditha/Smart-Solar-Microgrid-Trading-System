// Program.cs
// Purpose: Composition root for the Smart Solar Microgrid Trading System Web API. Wires up
// configuration, MongoDB, JWT authentication/authorization, email, and the auth/user/prosumer
// endpoints. Also supports a manual `dotnet run -- seed-admin` command that creates the
// initial Backoffice super-admin account without starting the web host — see
// Services/Seed/SuperAdminSeeder.cs and docs/authentication-implementation-approach.md.

using System.Text;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using smart_solar_mgt_api.Authorization;
using smart_solar_mgt_api.Configuration;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Endpoints;
using smart_solar_mgt_api.Services.Auth;
using smart_solar_mgt_api.Services.Email;
using smart_solar_mgt_api.Services.Prosumers;
using smart_solar_mgt_api.Services.Seed;
using smart_solar_mgt_api.Services.Users;

var builder = WebApplication.CreateBuilder(args);

// bind strongly-typed configuration sections (non-secret defaults in appsettings*.json;
// secrets via dotnet user-secrets locally or environment variables in production)
builder.Services.Configure<JwtOptions>(builder.Configuration.GetSection(JwtOptions.SectionName));
builder.Services.Configure<MongoOptions>(builder.Configuration.GetSection(MongoOptions.SectionName));
builder.Services.Configure<SmtpOptions>(builder.Configuration.GetSection(SmtpOptions.SectionName));
builder.Services.Configure<SeedOptions>(builder.Configuration.GetSection(SeedOptions.SectionName));
builder.Services.Configure<InvitationOptions>(builder.Configuration.GetSection(InvitationOptions.SectionName));
builder.Services.Configure<CorsOptions>(builder.Configuration.GetSection(CorsOptions.SectionName));

// the MongoDB client/database handle is thread-safe and long-lived, so it is registered once
builder.Services.AddSingleton<MongoContext>();

builder.Services.AddSingleton<IPasswordHasherService, PasswordHasherService>();
builder.Services.AddSingleton<IJwtTokenService, JwtTokenService>();
builder.Services.AddSingleton<ICookieAuthService, CookieAuthService>();
builder.Services.AddScoped<IRefreshTokenService, RefreshTokenService>();
builder.Services.AddScoped<IInvitationService, InvitationService>();
builder.Services.AddScoped<IUserService, UserService>();
builder.Services.AddScoped<IProsumerService, ProsumerService>();
builder.Services.AddScoped<IEmailSender, SmtpEmailSender>();
builder.Services.AddScoped<SuperAdminSeeder>();

builder.Services.AddOpenApi();

// the web frontend needs credentialed (cookie-carrying) cross-origin requests, which requires
// an explicit origin allowlist — a wildcard origin cannot be combined with AllowCredentials
var corsOrigins = builder.Configuration.GetSection(CorsOptions.SectionName).Get<CorsOptions>()?.AllowedOrigins ?? [];
builder.Services.AddCors(options =>
{
    options.AddPolicy("Frontend", policy =>
    {
        policy.WithOrigins(corsOrigins)
            .AllowAnyHeader()
            .AllowAnyMethod()
            .AllowCredentials();
    });
});

// configure JWT bearer authentication: issuer, audience, lifetime and signing key are all validated
var jwtSection = builder.Configuration.GetSection(JwtOptions.SectionName);

// HS256 requires a >=256-bit (32-byte) key; a shorter one passes config binding fine but throws
// a cryptic IDX10720 from deep inside token signing on the first login/refresh call — fail fast
// here instead, with a message that actually says what to fix
var signingKeyByteLength = Encoding.UTF8.GetByteCount(jwtSection[nameof(JwtOptions.SigningKey)] ?? string.Empty);
if (signingKeyByteLength < 32)
{
    throw new InvalidOperationException(
        $"Jwt:SigningKey must be at least 32 bytes (256 bits) for HS256; the configured key is {signingKeyByteLength} bytes. " +
        "Set a longer value, e.g.: dotnet user-secrets set \"Jwt:SigningKey\" \"<32+ character random string>\"");
}

builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        // JwtSecurityTokenHandler otherwise remaps short claim types (e.g. "sub") to their long
        // ClaimTypes URIs on the resulting principal by default, which breaks every
        // FindFirstValue(JwtRegisteredClaimNames.Sub) lookup in the user endpoints below
        options.MapInboundClaims = false;

        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = jwtSection[nameof(JwtOptions.Issuer)],
            ValidAudience = jwtSection[nameof(JwtOptions.Audience)],
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(jwtSection[nameof(JwtOptions.SigningKey)] ?? string.Empty)),
            ClockSkew = TimeSpan.FromSeconds(30)
        };

        // accept the access token from either the Authorization header (used today) or a cookie
        // (unused until a future web client sets one), so adding HttpOnly cookie delivery later
        // needs no change to this validation pipeline — see docs/authentication-implementation-approach.md, section 6
        options.Events = new JwtBearerEvents
        {
            OnMessageReceived = context =>
            {
                if (string.IsNullOrEmpty(context.Token) && context.Request.Cookies.TryGetValue(CookieAuthService.AccessTokenCookieName, out var cookieToken))
                {
                    context.Token = cookieToken;
                }

                return Task.CompletedTask;
            }
        };
    });

builder.Services.AddAuthorizationBuilder()
    .AddPolicy(RoleNames.Backoffice, policy => policy.RequireRole(RoleNames.Backoffice))
    .AddPolicy("ProsumerManagement", policy => policy.RequireRole(RoleNames.Backoffice, RoleNames.GridOperator));

var app = builder.Build();

// manual seed command: `dotnet run -- seed-admin` creates the initial Backoffice super-admin
// account and exits without starting the web host — it never runs automatically at startup
if (args.Contains("seed-admin"))
{
    using var seedScope = app.Services.CreateScope();
    var seeder = seedScope.ServiceProvider.GetRequiredService<SuperAdminSeeder>();
    return await seeder.RunAsync();
}

// ensure MongoDB indexes exist before serving traffic
using (var indexScope = app.Services.CreateScope())
{
    var mongoContext = indexScope.ServiceProvider.GetRequiredService<MongoContext>();
    await mongoContext.EnsureIndexesAsync();
}

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseHttpsRedirection();

app.UseCors("Frontend");
app.UseMiddleware<CsrfProtectionMiddleware>();

app.UseAuthentication();
app.UseAuthorization();

app.MapAuthEndpoints();
app.MapUserEndpoints();
app.MapProsumerEndpoints();

app.Run();

return 0;
