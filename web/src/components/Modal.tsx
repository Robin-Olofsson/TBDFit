import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

// A small, generic overlay dialog — no dependency added, just a fixed-position backdrop + centered
// card. First real use: ExerciseLibraryPanel's "Create Custom Exercise" form (see the reference
// image the developer shared, customexercise.png — a Hevy modal with this same header+close+form
// shape). Reusable for any future "open a form in a modal instead of inline" need without building
// a generic dialog framework — this is intentionally the smallest version of that idea.
interface Props {
  title: string
  onClose: () => void
  children: ReactNode
}

export default function Modal({ title, onClose, children }: Props) {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>{title}</h2>
          <button type="button" className="modal-close" aria-label="Close" onClick={onClose}>
            <X size={18} aria-hidden="true" />
          </button>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  )
}
