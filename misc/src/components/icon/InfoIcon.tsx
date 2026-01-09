export const InfoIcon = ({ size = 24, className = "" }) => {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      style={{ display: "inline-block", verticalAlign: "middle" }}
    >
      <defs>
        <linearGradient id="infoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" style={{ stopColor: "#06b6d4", stopOpacity: 1 }} />
          <stop
            offset="100%"
            style={{ stopColor: "#0891b2", stopOpacity: 1 }}
          />
        </linearGradient>
      </defs>

      <circle cx="12" cy="12" r="11" fill="url(#infoGradient)" opacity="0.2" />

      <circle
        cx="12"
        cy="12"
        r="10"
        fill="url(#infoGradient)"
        filter="url(#glow)"
      />

      <circle cx="12" cy="8" r="1.5" fill="white" />

      <rect x="10.5" y="11" width="3" height="7" rx="1.5" fill="white" />
    </svg>
  );
};
