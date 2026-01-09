export const TipIcon = ({ size = 24, className = "" }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    className={className}
    style={{ display: "inline-block", verticalAlign: "middle" }}
  >
    <defs>
      <linearGradient id="tipGradient" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#c084fc" />
        <stop offset="100%" stopColor="#f472b6" />
      </linearGradient>
    </defs>
    <circle cx="12" cy="12" r="11" fill="url(#tipGradient)" opacity="0.2" />
    <circle cx="12" cy="12" r="10" fill="url(#tipGradient)" />
    <path
      d="M12 8a4 4 0 0 1 4 4c0 2-2 2.5-2 4h-4c0-1.5-2-2-2-4a4 4 0 0 1 4-4zm0 9.5a1.5 1.5 0 0 0 1.5-1.5h-3A1.5 1.5 0 0 0 12 17.5z"
      fill="white"
    />
  </svg>
);
