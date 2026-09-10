import { useEffect, useRef, useState } from 'react'
import { MoreHorizontal } from 'lucide-react'

// A small "⋯" actions menu for a table row (first use: PlanPage.tsx's Routine list, replacing a
// separate "Open"/"Delete" column each — Open was always redundant with clicking the row itself,
// and a single menu scales to more than one per-row action, e.g. Duplicate, without adding another
// column per action). Same outside-click/Escape-to-close pattern as AccountMenu.tsx (no menu
// library needed for a handful of items) — kept as its own generic component rather than
// Routine-specific, since any other row-based list (Programs, later) can reuse it as-is.
export interface RowActionsMenuItem {
  label: string
  onSelect: () => void
  danger?: boolean
}

interface Props {
  // Accessible name for the trigger button — must describe WHICH row's actions this opens (e.g.
  // "Actions for Push Day"), since a page can render one of these per row and a screen reader user
  // needs to tell them apart without visual context.
  label: string
  items: RowActionsMenuItem[]
}

export default function RowActionsMenu({ label, items }: Props) {
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
    // stopPropagation so this never triggers an ancestor row's own onClick (e.g. PlanPage.tsx's
    // per-cell "navigate to detail" handlers) — opening/using this menu must never also navigate.
    <div className="row-actions-menu" ref={containerRef} onClick={(e) => e.stopPropagation()}>
      <button
        type="button"
        className="row-actions-menu-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
        onClick={() => setOpen((value) => !value)}
      >
        <MoreHorizontal size={18} aria-hidden="true" />
      </button>
      {open && (
        <div className="dropdown-panel row-actions-menu-dropdown" role="menu">
          {items.map((item) => (
            <button
              key={item.label}
              type="button"
              role="menuitem"
              className={item.danger ? 'dropdown-item dropdown-item-danger' : 'dropdown-item'}
              onClick={() => {
                setOpen(false)
                item.onSelect()
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
