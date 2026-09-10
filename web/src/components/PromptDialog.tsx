import { useState } from 'react'
import type { FormEvent } from 'react'
import Modal from './Modal'

// The themed, accessible replacement for window.prompt() — same Notification-vs-Confirmation split
// ConfirmDialog.tsx documents, plus a third: this is neither. It collects a single line of text
// before an action runs, so it deliberately does NOT reuse ConfirmDialog (that component's
// `destructive`/danger-button semantics and its "Cancel gets initial focus, not the primary action"
// reasoning are specific to an irreversible action — reusing it here would muddy that meaning for a
// plain create-and-name flow). It shares Modal.tsx's overlay/focus-trap/focus-restore machinery
// instead, at plain `role="dialog"` (not `alertdialog` — nothing here is an interruption demanding
// urgent Confirm/Cancel).
//
// `pending` is caller-owned (typically a TanStack mutation's own busy flag), matching ConfirmDialog's
// contract — this stays a dumb, controlled component with no opinion on what the confirmed value is
// used for or when the dialog closes (the caller closes it, normally on the action's own success).
interface Props {
  open: boolean
  title: string
  label: string
  placeholder?: string
  confirmLabel: string
  cancelLabel?: string
  pending?: boolean
  onConfirm: (value: string) => void
  onCancel: () => void
}

export default function PromptDialog({
  open,
  title,
  label,
  placeholder,
  confirmLabel,
  cancelLabel = 'Cancel',
  pending = false,
  onConfirm,
  onCancel,
}: Props) {
  const [value, setValue] = useState('')

  if (!open) return null

  const trimmed = value.trim()

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!trimmed || pending) return
    onConfirm(trimmed)
  }

  return (
    <Modal title={title} onClose={onCancel} closeOnEscape={!pending} closeOnBackdropClick={!pending}>
      <form onSubmit={handleSubmit}>
        <label className="field-label" htmlFor="prompt-dialog-input">
          {label}
        </label>
        <input
          id="prompt-dialog-input"
          className="table-input modal-field-input"
          type="text"
          placeholder={placeholder}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          disabled={pending}
        />
        <div className="modal-actions">
          <button type="button" className="btn-secondary" disabled={pending} onClick={onCancel}>
            {cancelLabel}
          </button>
          <button type="submit" className="btn-primary" disabled={pending || !trimmed}>
            {confirmLabel}
          </button>
        </div>
      </form>
    </Modal>
  )
}
