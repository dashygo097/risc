export const SuccessIcon = ({ size = 24, className = "" }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    className={className}
    style={{ display: "inline-block", verticalAlign: "middle" }}
  >
    <defs>
      <linearGradient id="successGradient" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#4ade80" />
        <stop offset="100%" stopColor="#22d3ee" />
      </linearGradient>
    </defs>
    <circle
      cx="12"
      cy="12"
      r="11"
      fill="url(#successGradient)"
      opacity="0.18"
    />
    <circle cx="12" cy="12" r="10" fill="url(#successGradient)" />
    <polyline
      points="8 13 11 16 16 10"
      stroke="white"
      strokeWidth="2"
      fill="none"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);
