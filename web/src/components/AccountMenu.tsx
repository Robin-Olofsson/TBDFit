import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

export interface AccountMenuProps {
  initial: string
  onSignOut: () => void
}

// Single top-bar account control: an icon button that opens a small menu (Profile & settings, Sign
// out) instead of separate always-visible "Profile" nav item + name label + Sign out button — per
// direct human UX feedback ("only a profile icon which opens a sub menu where profile settings
// are"). No menu library — a ref + one outside-click/Escape listener is enough for two items.
export default function AccountMenu({ initial, onSignOut }: AccountMenuProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handlePointerDown = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  return (
    <div className="account-menu" ref={containerRef}>
      <button
        type="button"
        className="account-menu-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account menu"
        onClick={() => setOpen((value) => !value)}
      >
        {initial}
      </button>
      {open && (
        <div className="dropdown-panel account-menu-dropdown" role="menu">
          <Link to="/profile" role="menuitem" className="dropdown-item account-menu-item" onClick={() => setOpen(false)}>
            Profile &amp; settings
          </Link>
          <button
            type="button"
            role="menuitem"
            className="dropdown-item account-menu-item dropdown-item-danger"
            onClick={() => {
              setOpen(false)
              onSignOut()
            }}
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  )
}
