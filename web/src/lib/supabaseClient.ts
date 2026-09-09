import { createClient } from '@supabase/supabase-js'

// The single shared Supabase client for the Web app — mirrors
// android/phone/src/main/kotlin/com/tbdfit/phone/backend/SupabaseClientProvider.kt's role and
// reasoning: one client, one shared auth session, not a separate instance per feature. Points at
// the SAME Supabase project as Android (same URL/anon key — see web/.env, populated from
// android/local.properties) so a TBDFit account is one identity across every client, never a
// separate Web user system.
const url = import.meta.env.VITE_SUPABASE_URL
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY

// Fail fast and loud on missing config, the same reasoning as SupabaseClientProvider's `check(...)`
// — an empty URL/key must not surface later as an opaque network error deep inside a sign-in
// attempt. Never logs the key value; the URL alone isn't a secret (see web/README.md).
if (!url || !anonKey) {
  throw new Error(
    'VITE_SUPABASE_URL and/or VITE_SUPABASE_ANON_KEY are missing. Copy web/.env.example to ' +
      'web/.env and fill in the same project values Android uses (see android/local.properties) — ' +
      'see web/README.md.',
  )
}

export const supabase = createClient(url, anonKey, {
  auth: {
    // Explicit even where it matches the library default (persistSession, detectSessionInUrl) so
    // the intent is documented, not accidental. flowType is NOT left at the library default
    // ('implicit'): PKCE is set explicitly, mirroring SupabaseClientProvider's own reasoning —
    // Supabase's docs note some email clients/scanners strip URL fragments (where implicit-flow
    // tokens travel) before a confirmation link ever reaches the app; PKCE carries a `code` query
    // parameter instead, which survives that. detectSessionInUrl=true means the client
    // auto-exchanges that code for a session on load, on whatever page the redirect lands on — no
    // dedicated callback route is required for this to work (see AuthContext.tsx).
    flowType: 'pkce',
    detectSessionInUrl: true,
    persistSession: true,
  },
})
