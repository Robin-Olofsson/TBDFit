import { useEffect, useRef, useState } from 'react'
import type { LucideIcon } from 'lucide-react'

// The generic version of what RestTimerPicker.tsx introduced: a field-styled trigger (looks like a
// text input, optional leading icon) that opens a `.dropdown-panel` list to choose from, instead of
// a native <select> — chosen so every "pick one of a short list of values" control in the app
// (Exercise Type, Equipment, Rest Timer, and whatever comes next) looks and behaves like the same
// system, not a native browser popup for some fields and a custom one for others. RestTimerPicker
// now wraps this directly rather than duplicating it — see that file.
export interface SelectFieldOption<T extends string> {
  value: T
  label: string
}

interface Props<T extends string> {
  id?: string
  value: T
  options: SelectFieldOption<T>[]
  onChange: (value: T) => void
  // Optional leading icon (e.g. RestTimerPicker's clock) — most Type/Equipment uses have none, so
  // this stays undefined there rather than forcing an icon where the field doesn't need one.
  icon?: LucideIcon
  // Required when there's no visible <label htmlFor> pointing at `id` (e.g. the Exercise
  // Type/Equipment filter row, which has no separate label text) — a button has no native
  // label-association fallback the way a real <select> does.
  ariaLabel?: string
  // Lets a call site's own layout rules reach the outer wrapper (e.g. `.exercise-library-filters
  // .select-field { flex: 1 }`) the same way they previously targeted `.table-input` directly.
  className?: string
  // Overrides what the closed trigger shows (defaults to the selected option's own `label`) — the
  // open list always shows full `label`s regardless. Exists for a compact trigger that can't fit a
  // full word (e.g. Set Type's 40px square showing just "N", while the open list still reads
  // "Normal"/"Warm-up"/"Failure"/"Drop set") — see PlannedExercisesEditor.tsx's use.
  triggerLabel?: string
}

export default function SelectField<T extends string>({
  id,
  value,
  options,
  onChange,
  icon: Icon,
  ariaLabel,
  className,
  triggerLabel,
}: Props<T>) {
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

  const fullLabel = options.find((option) => option.value === value)?.label ?? value
  const selectedLabel = triggerLabel ?? fullLabel

  return (
    <div className={className ? `select-field ${className}` : 'select-field'} ref={containerRef}>
      <button
        type="button"
        id={id}
        className="select-field-trigger"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={ariaLabel}
        // Only meaningful when triggerLabel shortens the display (e.g. "N" for "Normal") — a
        // native hover tooltip spelling out the full value, the same courtesy the old <option
        // title="..."> attribute gave a mouse user before this replaced the native <select>.
        title={triggerLabel ? fullLabel : undefined}
        onClick={() => setOpen((v) => !v)}
      >
        {Icon && <Icon size={14} aria-hidden="true" />}
        <span>{selectedLabel}</span>
      </button>
      {open && (
        <ul className="dropdown-panel select-field-list" role="listbox" aria-label={ariaLabel}>
          {options.map((option) => (
            <li key={option.value}>
              <button
                type="button"
                role="option"
                aria-selected={option.value === value}
                className={option.value === value ? 'dropdown-item dropdown-item-active' : 'dropdown-item'}
                onClick={() => {
                  onChange(option.value)
                  setOpen(false)
                }}
              >
                {option.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
