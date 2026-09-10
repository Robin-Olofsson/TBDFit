import { supabase } from '../lib/supabaseClient'

// The Web Daily Movement data boundary — read-only, same domain-module convention as
// data/routines.ts. `public.daily_activity` (supabase/migrations/20260919120000_create_daily_activity.sql)
// is a normalized, private, device-independent daily step total: RLS scopes every row to its own
// account, and there is no product requirement for Web to submit steps (no ingestion source exists
// on Web) — so this module is deliberately read-only. There is no insert/update/upsert function
// here, and none should be added without a real Web product requirement to write movement data.
//
// A date with no row means "no movement data for that day," never "zero steps" — this module does
// not fabricate missing days as zero; a future chart/consumer decides how to render a gap.
export interface DailyActivityPoint {
  date: string
  steps: number
}

interface DailyActivityRow {
  activity_date: string
  steps: number
}

// Inclusive date range, both bounds `YYYY-MM-DD`. Mirrors the exact RLS-protected query the
// migration documents in its own "READ CONTRACT" comment — no RPC/view, since a single-table
// date-range read has no N+1 problem for one to solve (unlike get_my_profile_summary(), which fans
// out across three unrelated tables).
export async function getDailyActivity(startDate: string, endDate: string): Promise<DailyActivityPoint[]> {
  const { data, error } = await supabase
    .from('daily_activity')
    .select('activity_date, steps')
    .gte('activity_date', startDate)
    .lte('activity_date', endDate)
    .order('activity_date', { ascending: true })
  if (error) throw error

  return (data as DailyActivityRow[]).map((row) => ({ date: row.activity_date, steps: row.steps }))
}

// No consumers yet this slice — the future Statistics tab is a separate, later slice. No hook is
// added here: this repo's actual convention (see data/routines.ts + its callers, e.g.
// HomePage.tsx/PlanPage.tsx) is a plain fetch function in data/*.ts, with `useQuery` and the
// `useAuth()`-derived userId wired at the consuming page/component — not inside the data module
// itself. `useOwnProfile`/`useMyProfileSummary` living in `auth/` and embedding that wiring is
// specific to identity/profile concerns, not the general data-layer pattern; introducing a second,
// hybrid convention here (a data/*.ts file importing `useAuth`) would be inconsistent with both.
// The future consumer wires `queryKeys.dailyActivity.range(userId, start, end)` +
// `getDailyActivity` itself, exactly as HomePage.tsx does today for `listMyRoutines`.
