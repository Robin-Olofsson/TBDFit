import { toast } from 'sonner'

// A single small wrapper around Sonner's own toast.promise, not a generic "notification system" —
// Sonner already is the notification system (loading/success/error state, accessible live region,
// auto-dismiss, survives route navigation via the app-root-mounted <Toaster/> in App.tsx). This
// exists only so the next call site (Routine deleted, Program saved, Profile updated, Exercise
// created) adds one small typed function here instead of duplicating toast.promise's call shape and
// re-deciding message copy conventions inline at each page.
interface PromiseToastMessages {
  loading: string
  success: string
  error: string
}

// Returns the same promise unmodified (Sonner only subscribes to it, never wraps/replaces it) so
// the caller can still use it exactly as before — see RoutineEditorPage.tsx's use of
// `saveMutation.mutateAsync()`, whose existing onSuccess/onError side effects (cache invalidation,
// navigation, the inline form-error message) are untouched by also handing this same promise to a
// toast.
function notifyPromise<T>(promise: Promise<T>, messages: PromiseToastMessages): Promise<T> {
  toast.promise(promise, messages)
  return promise
}

export function notifyRoutineSaved(promise: Promise<string>): Promise<string> {
  return notifyPromise(promise, {
    loading: 'Saving routine...',
    success: 'Routine saved',
    error: 'Could not save routine',
  })
}

export function notifyRoutineDeleted(promise: Promise<void>): Promise<void> {
  return notifyPromise(promise, {
    loading: 'Deleting routine...',
    success: 'Routine deleted',
    error: 'Could not delete routine',
  })
}

export function notifyRoutineDuplicated(promise: Promise<string>): Promise<string> {
  return notifyPromise(promise, {
    loading: 'Duplicating routine...',
    success: 'Routine duplicated',
    error: 'Could not duplicate routine',
  })
}

export function notifyProgramDeleted(promise: Promise<void>): Promise<void> {
  return notifyPromise(promise, {
    loading: 'Deleting program...',
    success: 'Program deleted',
    error: 'Could not delete program',
  })
}

export function notifyProgramWeekDeleted(promise: Promise<void>): Promise<void> {
  return notifyPromise(promise, {
    loading: 'Deleting week...',
    success: 'Week deleted',
    error: 'Could not delete week',
  })
}

export function notifyProgramSessionDeleted(promise: Promise<void>): Promise<void> {
  return notifyPromise(promise, {
    loading: 'Deleting session...',
    success: 'Session deleted',
    error: 'Could not delete session',
  })
}

export function notifyProfileUpdated<T>(promise: Promise<T>): Promise<T> {
  return notifyPromise(promise, {
    loading: 'Saving profile...',
    success: 'Profile updated',
    error: 'Could not update profile',
  })
}

export function notifySessionScheduled<T>(promise: Promise<T>): Promise<T> {
  return notifyPromise(promise, {
    loading: 'Scheduling session...',
    success: 'Session scheduled',
    error: 'Could not schedule session',
  })
}

export function notifySessionRescheduled<T>(promise: Promise<T>): Promise<T> {
  return notifyPromise(promise, {
    loading: 'Rescheduling session...',
    success: 'Session rescheduled',
    error: 'Could not reschedule session',
  })
}

export function notifySessionUnscheduled(promise: Promise<void>): Promise<void> {
  return notifyPromise(promise, {
    loading: 'Removing session...',
    success: 'Session unscheduled',
    error: 'Could not unschedule session',
  })
}

// For the informational case this app previously used a blocking window.alert() for (see
// ProgramsListPage.tsx's create-program error) — not a promise-driven action, just a single message
// to surface.
export function notifyError(message: string): void {
  toast.error(message)
}
