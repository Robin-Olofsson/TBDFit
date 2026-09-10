import Modal from './Modal'

// A destructive-action confirmation — the counterpart to Modal.tsx's plain content/form dialog, and
// the shared component every "Delete X?" flow renders (Routine, Program, Program Week, Program
// Session — see each page's own use), never Delete-Routine-specific modal code. The
// Notification-vs-Confirmation split this belongs to: a toast (lib/toast.ts) reports something that
// already happened; this asks permission before doing something destructive in the first place —
// never a native window.confirm(), which cannot be styled and exposes browser-origin text.
//
// `pending` is caller-owned (typically a TanStack `useMutation`'s own `isPending`), not managed
// internally — this stays a dumb, controlled component with no opinion on how the confirmed action
// actually runs or when the dialog closes (the caller closes it, normally from the mutation's own
// onSuccess, once the server has actually confirmed the delete — never optimistically). While
// pending, both buttons disable and Escape/backdrop-click are suppressed (see Modal.tsx's
// closeOnEscape/closeOnBackdropClick), so a request already in flight can't be dismissed out from
// under itself.
//
// Cancel renders before Confirm in the DOM so Modal's focus-trap gives it initial focus, not the
// destructive action — an accidental Enter/Space press must never trigger the delete.
interface Props {
  open: boolean
  title: string
  description: string
  confirmLabel: string
  cancelLabel?: string
  destructive?: boolean
  pending?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = 'Cancel',
  destructive = false,
  pending = false,
  onConfirm,
  onCancel,
}: Props) {
  if (!open) return null

  return (
    <Modal
      title={title}
      onClose={onCancel}
      role="alertdialog"
      hideCloseButton
      closeOnEscape={!pending}
      closeOnBackdropClick={!pending}
    >
      <p className="confirm-dialog-description">{description}</p>
      <div className="modal-actions confirm-dialog-actions">
        <button type="button" className="btn-secondary" disabled={pending} onClick={onCancel}>
          {cancelLabel}
        </button>
        <button
          type="button"
          className={destructive ? 'btn-primary btn-danger' : 'btn-primary'}
          disabled={pending}
          onClick={onConfirm}
        >
          {confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
