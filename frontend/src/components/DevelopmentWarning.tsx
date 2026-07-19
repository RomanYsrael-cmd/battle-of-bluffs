export function DevelopmentWarning() {
  return (
    <div className="development-warning" role="note">
      <strong>Development identity only</strong>
      <span>Temporary player IDs are stored per tab and are not real authentication.</span>
    </div>
  )
}
