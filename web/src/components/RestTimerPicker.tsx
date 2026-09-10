import { Timer } from 'lucide-react'
import SelectField from './SelectField'
import { REST_TIMER_OPTIONS } from '../lib/plannedExerciseDrafts'

// A thin RestTimer-specific wrapper around the generic SelectField (see that file) — converts
// between this field's real value shape (seconds, or null for Off) and SelectField's string-only
// option values, since REST_TIMER_OPTIONS.seconds is `number | null` and SelectField needs a plain
// string to key its options by. `OFF_VALUE` is purely an internal sentinel, never persisted or sent
// anywhere outside this file.
const OFF_VALUE = 'off'

interface Props {
  id?: string
  value: number | null
  onChange: (seconds: number | null) => void
}

export default function RestTimerPicker({ id, value, onChange }: Props) {
  const options = REST_TIMER_OPTIONS.map((option) => ({
    value: option.seconds === null ? OFF_VALUE : String(option.seconds),
    label: option.label,
  }))

  return (
    <SelectField
      id={id}
      value={value === null ? OFF_VALUE : String(value)}
      options={options}
      icon={Timer}
      ariaLabel="Rest timer"
      onChange={(v) => onChange(v === OFF_VALUE ? null : Number(v))}
    />
  )
}
