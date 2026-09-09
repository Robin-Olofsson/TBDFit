import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'

interface NavItem {
  to: string
  label: string
  end: boolean
}

const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Home', end: true },
  // Label is "Routine" (per direct human UX feedback) — the route itself stays /plan, an internal
  // implementation detail; see PlanPage.tsx/RoutineDetailPage.tsx for the matching visible rename.
  { to: '/plan', label: 'Routine', end: false },
  // Programs is its own sibling nav item, deliberately NOT nested under /plan — Option C from
  // docs/product/web-routine-program-planning-research.md's Routine Information Architecture
  // section: zero change to the already-shipped, real Routine nav item/routes, and Program can
  // never read as a requirement for, or parent of, Routine if they are simply two sibling
  // destinations from the moment Program exists.
  { to: '/programs', label: 'Programs', end: false },
  { to: '/history', label: 'History', end: false },
]

// Top nav with a sliding blue underline highlighting the current destination and animating to the
// newly selected one — per direct human UX feedback. Measured via refs (offsetLeft/offsetWidth),
// not a fixed per-item width, since "Home"/"Plan"/"History" aren't the same length. No animation
// library: a single CSS `transition` on the indicator's transform/width does the movement.
export default function TopNav() {
  const location = useLocation()
  const containerRef = useRef<HTMLElement>(null)
  const itemRefs = useRef<Record<string, HTMLAnchorElement | null>>({})
  const [indicator, setIndicator] = useState<{ left: number; width: number } | null>(null)

  useEffect(() => {
    const measure = () => {
      const active = NAV_ITEMS.find((item) => (item.end ? location.pathname === item.to : location.pathname.startsWith(item.to)))
      const el = active ? itemRefs.current[active.to] : null
      if (el && containerRef.current) {
        setIndicator({ left: el.offsetLeft, width: el.offsetWidth })
      } else {
        setIndicator(null)
      }
    }
    measure()
    // Re-measure on resize: the container's available width (and so where items sit) can change
    // even though the items' own text never does.
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  }, [location.pathname])

  return (
    <nav className="topbar-nav" ref={containerRef}>
      {NAV_ITEMS.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          ref={(el) => {
            itemRefs.current[item.to] = el
          }}
          className={({ isActive }) => (isActive ? 'topbar-nav-item topbar-nav-item-active' : 'topbar-nav-item')}
        >
          {item.label}
        </NavLink>
      ))}
      {indicator && (
        <span
          className="topbar-nav-indicator"
          style={{ transform: `translateX(${indicator.left}px)`, width: `${indicator.width}px` }}
          aria-hidden="true"
        />
      )}
    </nav>
  )
}
