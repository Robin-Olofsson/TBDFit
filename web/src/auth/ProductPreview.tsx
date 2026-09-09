// PROTOTYPE/PRESENTATION-ONLY hero mockup — hardcoded content, no data fetching, not the real
// Plan/History/Profile pages (those live in src/pages/). Built as real HTML/CSS, not an embedded
// screenshot. Deliberately uses the CURRENT real sidebar shape/labels (Plan / History / Profile —
// see docs/product/web-information-architecture.md §2) rather than the approved visual reference's
// outdated nav ("Home/Workouts/Routines/Exercises/Progress"), so this marketing content can never
// be mistaken for a competing, undocumented IA proposal. Every fake interactive-looking element
// (nav items, buttons) is tabIndex={-1}: aria-hidden on an ancestor does not reliably stop
// keyboard-tab focus from landing on a real <button>, so this is set explicitly rather than assumed.
export default function ProductPreview() {
  return (
    <div className="preview-stack">
      <div className="preview-desktop">
        <div className="preview-browser-bar">
          <span className="preview-dot" />
          <span className="preview-dot" />
          <span className="preview-dot" />
        </div>
        <div className="preview-desktop-body">
          <aside className="preview-sidebar">
            <div className="preview-sidebar-brand">TBDFit</div>
            <span className="preview-nav-item preview-nav-item-active">Plan</span>
            <span className="preview-nav-item">History</span>
            <span className="preview-nav-item">Profile</span>
          </aside>
          <div className="preview-content">
            <p className="preview-greeting">Good morning, Alex 👋</p>
            <div className="preview-stat-row">
              <div className="preview-stat">
                <div className="preview-stat-value">24</div>
                <div className="preview-stat-label">Workouts this month</div>
              </div>
              <div className="preview-stat">
                <div className="preview-stat-value">12,450 kg</div>
                <div className="preview-stat-label">Total volume</div>
              </div>
              <div className="preview-stat">
                <div className="preview-stat-value">8 days</div>
                <div className="preview-stat-label">Current streak</div>
              </div>
            </div>
            <div className="preview-columns">
              <div>
                <p className="preview-column-title">Your Plan</p>
                <p className="preview-list-item">Push Day</p>
                <p className="preview-list-item">Pull Day</p>
                <p className="preview-list-item">Leg Day</p>
              </div>
              <div>
                <p className="preview-column-title">Recent Activity</p>
                <p className="preview-list-item">Push Day — Today</p>
                <p className="preview-list-item">Pull Day — 2 days ago</p>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div className="preview-phone">
        <div className="preview-phone-header">
          <span>Bench Press</span>
          <span className="preview-phone-timer">24:17</span>
        </div>
        <div className="preview-phone-set preview-phone-set-done">
          <span>1</span>
          <span>80 kg</span>
          <span>8</span>
          <span>✓</span>
        </div>
        <div className="preview-phone-set preview-phone-set-done">
          <span>2</span>
          <span>80 kg</span>
          <span>8</span>
          <span>✓</span>
        </div>
        <div className="preview-phone-set preview-phone-set-active">
          <span>3</span>
          <span>80 kg</span>
          <span>6</span>
          <span>—</span>
        </div>
        <button type="button" className="preview-phone-add" tabIndex={-1}>
          + Add Set
        </button>
        <button type="button" className="preview-phone-log" tabIndex={-1}>
          Log Set
        </button>
      </div>
    </div>
  )
}
