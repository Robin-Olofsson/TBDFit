import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

// A small, generic overlay dialog — no dependency added, just a fixed-position backdrop + centered
// card. First real use: ExerciseLibraryPanel's "Create Custom Exercise" form (see the reference
// image the developer shared, customexercise.png — a Hevy modal with this same header+close+form
// shape). Reusable for any future "open a form in a modal instead of inline" need without building
// a generic dialog framework — this is intentionally the smallest version of that idea.
//
// Also the base for ConfirmDialog.tsx (destructive confirmations) — hence `role`/`closeOnEscape`/
// `closeOnBackdropClick`/`hideCloseButton` beyond the original Custom Exercise use. Real modal
// semantics, not just a styled overlay <div>: focus moves into the dialog on open, Tab/Shift+Tab is
// trapped inside it, and focus returns to whatever triggered the dialog on close.
interface Props {
  title: string
  onClose: () => void
  children: ReactNode
  // 'dialog' (default) for ordinary content/forms; 'alertdialog' for an interruption that demands
  // an immediate Confirm/Cancel response — the correct ARIA distinction per the WAI-ARIA Authoring
  // Practices (see ConfirmDialog.tsx), not a stylistic choice.
  role?: 'dialog' | 'alertdialog'
  // Both default true (the original Custom Exercise modal's unchanged behavior). ConfirmDialog
  // passes both false while a destructive action is in flight, so a stray Escape press or
  // backdrop click can't dismiss the dialog out from under a request that's already running.
  closeOnEscape?: boolean
  closeOnBackdropClick?: boolean
  // Hides only the header's X control (the title itself still renders and still supplies the
  // dialog's accessible name) — ConfirmDialog has no "close without deciding" affordance, only
  // explicit Cancel/Confirm buttons in its own footer.
  hideCloseButton?: boolean
}

export default function Modal({
  title,
  onClose,
  children,
  role = 'dialog',
  closeOnEscape = true,
  closeOnBackdropClick = true,
  hideCloseButton = false,
}: Props) {
  const cardRef = useRef<HTMLDivElement>(null)
  const previouslyFocusedRef = useRef<HTMLElement | null>(null)

  // Focus enters the dialog on mount, and returns to whatever element had focus before the dialog
  // opened once it unmounts — real dialog behavior, not just visual overlay. Runs once per mount,
  // deliberately not re-run on prop changes (re-focusing on every re-render would fight the user).
  useEffect(() => {
    previouslyFocusedRef.current = document.activeElement as HTMLElement | null
    const focusable = getFocusableElements(cardRef.current)
    ;(focusable[0] ?? cardRef.current)?.focus()

    return () => {
      previouslyFocusedRef.current?.focus()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (closeOnEscape) onClose()
        return
      }
      if (e.key !== 'Tab') return
      // Trap Tab/Shift+Tab inside the dialog — background content must not receive focus while
      // this is open (see the accessibility requirement this satisfies: "background interaction is
      // blocked while open").
      const focusable = getFocusableElements(cardRef.current)
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose, closeOnEscape])

  return (
    <div className="modal-overlay" onClick={closeOnBackdropClick ? onClose : undefined}>
      <div
        ref={cardRef}
        className="modal-card"
        role={role}
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h2>{title}</h2>
          {!hideCloseButton && (
            <button type="button" className="modal-close" aria-label="Close" onClick={onClose}>
              <X size={18} aria-hidden="true" />
            </button>
          )}
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  )
}

function getFocusableElements(container: HTMLElement | null): HTMLElement[] {
  if (!container) return []
  return Array.from(
    container.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'),
  ).filter((el) => !el.hasAttribute('disabled'))
}
