import { describe, expect, it, vi } from 'vitest'

// Pure-logic test only, per this project's no-jsdom/no-component-rendering convention (see
// routines.test.ts's own doc comment) — mocks Sonner's toast.promise/error/info to verify the exact
// copy this app promises for each flow, without rendering anything.
const toastPromiseMock = vi.fn()
const toastErrorMock = vi.fn()
vi.mock('sonner', () => ({ toast: { promise: toastPromiseMock, error: toastErrorMock } }))

const {
  notifyRoutineSaved,
  notifyRoutineDeleted,
  notifyRoutineDuplicated,
  notifyProgramDeleted,
  notifyProgramWeekDeleted,
  notifyProgramSessionDeleted,
  notifyError,
} = await import('./toast')

describe('notifyRoutineSaved', () => {
  it('wires the exact required loading/success/error copy and returns the same promise', async () => {
    const promise = Promise.resolve('routine-1')

    const returned = notifyRoutineSaved(promise)

    expect(toastPromiseMock).toHaveBeenCalledWith(promise, {
      loading: 'Saving routine...',
      success: 'Routine saved',
      error: 'Could not save routine',
    })
    expect(returned).toBe(promise)
    await promise
  })
})

describe('delete notifications', () => {
  it('notifyRoutineDeleted wires the routine-delete copy and returns the same promise', async () => {
    const promise = Promise.resolve()
    const returned = notifyRoutineDeleted(promise)
    expect(toastPromiseMock).toHaveBeenCalledWith(promise, {
      loading: 'Deleting routine...',
      success: 'Routine deleted',
      error: 'Could not delete routine',
    })
    expect(returned).toBe(promise)
    await promise
  })

  it('notifyRoutineDuplicated wires the routine-duplicate copy and returns the same promise', async () => {
    const promise = Promise.resolve('routine-2')
    const returned = notifyRoutineDuplicated(promise)
    expect(toastPromiseMock).toHaveBeenCalledWith(promise, {
      loading: 'Duplicating routine...',
      success: 'Routine duplicated',
      error: 'Could not duplicate routine',
    })
    expect(returned).toBe(promise)
    await promise
  })

  it('notifyProgramDeleted wires the program-delete copy', async () => {
    const promise = Promise.resolve()
    notifyProgramDeleted(promise)
    expect(toastPromiseMock).toHaveBeenCalledWith(promise, {
      loading: 'Deleting program...',
      success: 'Program deleted',
      error: 'Could not delete program',
    })
    await promise
  })

  it('notifyProgramWeekDeleted wires the week-delete copy', async () => {
    const promise = Promise.resolve()
    notifyProgramWeekDeleted(promise)
    expect(toastPromiseMock).toHaveBeenCalledWith(promise, {
      loading: 'Deleting week...',
      success: 'Week deleted',
      error: 'Could not delete week',
    })
    await promise
  })

  it('notifyProgramSessionDeleted wires the session-delete copy', async () => {
    const promise = Promise.resolve()
    notifyProgramSessionDeleted(promise)
    expect(toastPromiseMock).toHaveBeenCalledWith(promise, {
      loading: 'Deleting session...',
      success: 'Session deleted',
      error: 'Could not delete session',
    })
    await promise
  })
})

describe('notifyError', () => {
  it('delegates to toast.error with the given message', () => {
    notifyError('Failed to create program.')
    expect(toastErrorMock).toHaveBeenCalledWith('Failed to create program.')
  })
})
