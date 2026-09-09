import DecorativeCurve from './DecorativeCurve'
import ProductPreview from './ProductPreview'

// The right-hand marketing/product panel for /login — PROTOTYPE/PRESENTATION-ONLY (see
// docs/product/frontend-prototype-notes.md): nothing here fetches real data or represents
// committed backend/product scope. The decorative curve and the fake product mockup are
// aria-hidden (they're non-interactive/misleading to assistive tech if announced as real
// controls); the headline/copy text is left in the normal accessibility tree since it's genuine,
// static marketing copy a screen-reader user may reasonably want to hear.
export default function AuthHero() {
  return (
    <aside className="login-hero">
      <div aria-hidden="true">
        <DecorativeCurve />
      </div>
      <div className="login-hero-eyebrow">
        <span>BUILD TODAY</span>
        <span>A BRIGHTER YOU</span>
      </div>
      <div className="login-hero-copy">
        <h2 className="login-hero-heading">
          More Than Workouts
          <br />
          <span className="login-hero-accent">A Stronger You</span>
        </h2>
        <p className="login-hero-subtitle">
          Plan. Track. Progress. TBDFit gives you the tools, structure and motivation to reach your
          goals.
        </p>
      </div>
      <div aria-hidden="true">
        <ProductPreview />
      </div>
    </aside>
  )
}
