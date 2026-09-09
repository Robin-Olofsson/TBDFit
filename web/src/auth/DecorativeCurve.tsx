// Subtle, non-interactive decorative accent recreating the approved reference's blue curved-line
// motif via inline SVG — never an embedded screenshot/image. Purely presentational: the caller
// marks it aria-hidden, and pointer-events are disabled here too so it can never intercept clicks
// even if something is layered over it later. No animation — a restrained static line only.
export default function DecorativeCurve() {
  return (
    <svg className="login-hero-curve" viewBox="0 0 100 100" preserveAspectRatio="none" focusable="false">
      <path
        d="M 8 100 C 8 62 30 42 55 32 C 72 25 82 15 86 2"
        fill="none"
        stroke="var(--color-accent)"
        strokeWidth="0.6"
        strokeLinecap="round"
        opacity="0.4"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  )
}
