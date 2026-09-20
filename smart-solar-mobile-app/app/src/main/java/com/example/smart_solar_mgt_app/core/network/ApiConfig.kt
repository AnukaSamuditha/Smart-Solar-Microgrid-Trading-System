package com.example.smart_solar_mgt_app.core.network

import com.example.smart_solar_mgt_app.BuildConfig

/**
 * Points the app at the smart-solar-mgt-api Web Service (see project-specification.md section 5).
 * Backed by the API_BASE_URL property in local.properties (module root, gitignored) via the
 * generated BuildConfig field - see the comment above `apiBaseUrl` in app/build.gradle.kts.
 * Defaults to 10.0.2.2, the Android emulator's alias for the host machine's loopback address,
 * where `dotnet run --launch-profile http` serves the API on port 5011 during development.
 * Override it per developer/device by adding `API_BASE_URL=http://<host>:<port>` to
 * local.properties - e.g. a LAN IP for a physical device, or an HTTPS host for staging/prod
 * (see network_security_config.xml, which only allows cleartext for local dev addresses).
 */
object ApiConfig {
    val BASE_URL: String = BuildConfig.API_BASE_URL
}
