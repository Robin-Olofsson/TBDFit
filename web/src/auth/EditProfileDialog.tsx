import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import Modal from '../components/Modal'
import { updateOwnProfile } from './profileCreation'
import { useAuth } from './AuthContext'
import { queryKeys } from '../queryKeys'
import { notifyProfileUpdated, notifyError } from '../lib/toast'

const BIO_MAX_LENGTH = 280

interface Props {
  open: boolean
  username: string
  displayName: string
  bio: string | null
  onClose: () => void
}

// The Edit Profile flow for the fields this backend actually supports today: display name and bio.
// Username is rendered READ-ONLY, not editable — see profileCreation.ts's updateOwnProfile for why:
// username-change policy has not been designed/reviewed (distinct from username-creation's existing
// uniqueness handling), so this dialog does not pretend it's safe to mutate.
export default function EditProfileDialog({ open, username, displayName, bio, onClose }: Props) {
  const { session } = useAuth()
  const queryClient = useQueryClient()
  const userId = session?.user.id ?? ''

  const [displayNameValue, setDisplayNameValue] = useState(displayName)
  const [bioValue, setBioValue] = useState(bio ?? '')

  const mutation = useMutation({
    mutationFn: () => updateOwnProfile(displayNameValue.trim(), bioValue.trim() === '' ? null : bioValue.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.profile.detail(userId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.profile.summary(userId) })
      onClose()
    },
    onError: () => {
      // The toast below already surfaces a friendly message via notifyProfileUpdated — this
      // handler exists only so react-query doesn't log an unhandled rejection; no raw Postgrest
      // error ever reaches the user.
    },
  })

  if (!open) return null

  const trimmedName = displayNameValue.trim()
  const bioTooLong = bioValue.length > BIO_MAX_LENGTH

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!trimmedName || bioTooLong || mutation.isPending) return
    notifyProfileUpdated(mutation.mutateAsync()).catch(() => notifyError('Could not update profile'))
  }

  return (
    <Modal title="Edit Profile" onClose={onClose} closeOnEscape={!mutation.isPending} closeOnBackdropClick={!mutation.isPending}>
      <form onSubmit={handleSubmit}>
        <label className="field-label" htmlFor="edit-profile-display-name">
          Display name
        </label>
        <input
          id="edit-profile-display-name"
          className="table-input modal-field-input"
          type="text"
          value={displayNameValue}
          onChange={(e) => setDisplayNameValue(e.target.value)}
          disabled={mutation.isPending}
          required
        />

        <label className="field-label" htmlFor="edit-profile-username">
          Username
        </label>
        <input
          id="edit-profile-username"
          className="table-input modal-field-input"
          type="text"
          value={`@${username}`}
          disabled
          readOnly
        />
        <p className="field-hint">Username can't be changed yet.</p>

        <label className="field-label" htmlFor="edit-profile-bio">
          Bio
        </label>
        <textarea
          id="edit-profile-bio"
          className="table-input modal-field-input edit-profile-bio-field"
          value={bioValue}
          onChange={(e) => setBioValue(e.target.value)}
          disabled={mutation.isPending}
          maxLength={BIO_MAX_LENGTH + 20}
          rows={3}
        />
        <p className={bioTooLong ? 'field-hint field-hint-error' : 'field-hint'}>
          {bioValue.length}/{BIO_MAX_LENGTH}
        </p>

        <div className="modal-actions">
          <button type="button" className="btn-secondary" disabled={mutation.isPending} onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn-primary" disabled={mutation.isPending || !trimmedName || bioTooLong}>
            Save
          </button>
        </div>
      </form>
    </Modal>
  )
}
