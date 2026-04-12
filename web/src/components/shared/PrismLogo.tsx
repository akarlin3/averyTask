interface PrismLogoProps {
  variant?: 'full' | 'icon';
  size?: number;
  className?: string;
}

export function PrismLogo({ variant = 'full', size = 40, className = '' }: PrismLogoProps) {
  const iconSize = size;
  const fontSize = size * 0.55;

  return (
    <div className={`inline-flex items-center gap-2.5 ${className}`}>
      {/* Prism icon - geometric triangle with spectral gradient */}
      <svg
        width={iconSize}
        height={iconSize}
        viewBox="0 0 48 48"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
      >
        <defs>
          <linearGradient id="prism-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#818cf8" />
            <stop offset="50%" stopColor="#6366f1" />
            <stop offset="100%" stopColor="#4f46e5" />
          </linearGradient>
          <linearGradient id="prism-light" x1="24" y1="6" x2="24" y2="42">
            <stop offset="0%" stopColor="white" stopOpacity="0.3" />
            <stop offset="100%" stopColor="white" stopOpacity="0" />
          </linearGradient>
        </defs>
        {/* Main prism triangle */}
        <path
          d="M24 4L44 40H4L24 4Z"
          fill="url(#prism-gradient)"
          stroke="rgba(255,255,255,0.1)"
          strokeWidth="0.5"
        />
        {/* Light refraction overlay */}
        <path
          d="M24 4L44 40H4L24 4Z"
          fill="url(#prism-light)"
        />
        {/* Inner facet lines */}
        <path
          d="M24 4L18 40"
          stroke="rgba(255,255,255,0.15)"
          strokeWidth="0.75"
        />
        <path
          d="M24 4L32 40"
          stroke="rgba(255,255,255,0.1)"
          strokeWidth="0.75"
        />
        {/* Spectral rays on right side */}
        <line x1="34" y1="26" x2="46" y2="22" stroke="#ef4444" strokeWidth="1.5" strokeLinecap="round" opacity="0.7" />
        <line x1="35" y1="29" x2="46" y2="27" stroke="#f59e0b" strokeWidth="1.5" strokeLinecap="round" opacity="0.7" />
        <line x1="36" y1="32" x2="46" y2="32" stroke="#22c55e" strokeWidth="1.5" strokeLinecap="round" opacity="0.7" />
        <line x1="37" y1="35" x2="46" y2="37" stroke="#3b82f6" strokeWidth="1.5" strokeLinecap="round" opacity="0.7" />
      </svg>

      {/* Wordmark */}
      {variant === 'full' && (
        <span
          className="font-bold tracking-tight text-[var(--color-text-primary)]"
          style={{ fontSize }}
        >
          Prism<span className="text-[var(--color-accent)]">Task</span>
        </span>
      )}
    </div>
  );
}
