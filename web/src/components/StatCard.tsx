// Small reusable presentational card — used by Home (dashboard stats) and History (progress
// stats). Not a design-system component library, just one shared shape to avoid duplicating markup
// (see review brief §25: small reusable components encouraged, a large abstraction is not).
export interface StatCardProps {
  label: string
  value: string
  sublabel?: string
}

export default function StatCard({ label, value, sublabel }: StatCardProps) {
  return (
    <div className="stat-card">
      <div className="stat-card-label">{label}</div>
      <div className="stat-card-value">{value}</div>
      {sublabel && <div className="stat-card-sublabel">{sublabel}</div>}
    </div>
  )
}
