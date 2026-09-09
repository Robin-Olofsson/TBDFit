import { Link } from 'react-router-dom'

// A real <Link> (not a div/button with onClick) so this is a genuine, keyboard-accessible
// navigation element — same accessibility bar as the rest of the prototype (see /login's own
// focus-visible work). Used by Home's "Your Plan" section; PlanPage's own table stays as-is.
export interface PlanCardProps {
  title: string
  subtitle: string
  to: string
}

export default function PlanCard({ title, subtitle, to }: PlanCardProps) {
  return (
    <Link to={to} className="plan-card">
      <div className="plan-card-title">{title}</div>
      <div className="plan-card-subtitle">{subtitle}</div>
    </Link>
  )
}
