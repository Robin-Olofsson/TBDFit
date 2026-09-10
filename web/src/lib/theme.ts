// Single source of truth for "what does TBDFit actually look like right now" — read from the CSS's
// own authoritative `color-scheme` declaration (index.css's `:root { color-scheme: dark }`) rather
// than a second hardcoded "dark" string living independently in App.tsx's <Toaster theme="dark">.
// This app has no user-facing Light/Dark/System preference today — verified: no ThemeContext, no
// theme toggle, no `prefers-color-scheme` branching exists anywhere in this codebase. It is
// unconditionally dark by CSS design. Deliberately NOT `theme="system"`: that would make Sonner
// render a light toast surface whenever the visiting OS is in light mode, while the rest of the app
// stays hard-dark regardless — a real visual mismatch, not a fix. If/when TBDFit adds a real theme
// preference, this file's `getAppColorScheme` body is the one place that needs to change; nothing
// else (Sonner's <Toaster theme={...}>, or any future consumer) should need to know how the value
// is derived.
//
// Split into a pure function (testable without a DOM) and a thin DOM-reading wrapper (verified by
// direct code reading + a real build, per this project's no-jsdom testing convention — see
// toast.test.ts's own doc comment for the same pattern).
export function resolveColorScheme(computedColorScheme: string): 'light' | 'dark' {
  return computedColorScheme.includes('light') ? 'light' : 'dark'
}

export function getAppColorScheme(): 'light' | 'dark' {
  return resolveColorScheme(getComputedStyle(document.documentElement).colorScheme)
}
