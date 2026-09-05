package com.tbdfit.phone.backend

import android.util.Log
import com.tbdfit.phone.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

private const val LOG_TAG = "TBDFit.sync"

// The single shared Supabase client for the app. Every capability-specific remote store (see
// ADR-004) uses this same instance rather than constructing its own — one client means one shared
// auth session, not a separate, conflicting session per capability. This is unavoidable wiring,
// not a generic backend abstraction: there is exactly one Supabase project, so exactly one client.
object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        // Fail fast and loud on missing config, rather than letting an empty URL/key surface later
        // as an opaque network exception deep inside a sync attempt. Only presence and the (public,
        // non-secret) URL are logged — never the key value.
        val urlConfigured = BuildConfig.SUPABASE_URL.isNotBlank()
        val keyConfigured = BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
        Log.d(
            LOG_TAG,
            "config: SUPABASE_URL configured=$urlConfigured" +
                (if (urlConfigured) " host=${BuildConfig.SUPABASE_URL}" else ""),
        )
        Log.d(LOG_TAG, "config: SUPABASE_ANON_KEY configured=$keyConfigured")
        check(urlConfigured && keyConfigured) {
            "SUPABASE_URL and/or SUPABASE_ANON_KEY are blank. Add them to android/local.properties " +
                "(SUPABASE_URL=..., SUPABASE_ANON_KEY=...) and rebuild — see README/ADR-004 setup notes."
        }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
