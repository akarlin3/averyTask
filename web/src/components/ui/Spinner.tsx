type SpinnerSize = 'sm' | 'md' | 'lg';

interface SpinnerProps {
  size?: SpinnerSize;
  text?: string;
  className?: string;
}

const sizeStyles: Record<SpinnerSize, string> = {
  sm: 'h-4 w-4 border-2',
  md: 'h-6 w-6 border-2',
  lg: 'h-10 w-10 border-3',
};

export function Spinner({ size = 'md', text, className = '' }: SpinnerProps) {
  return (
    <div className={`inline-flex items-center gap-2 ${className}`}>
      <div
        className={`animate-spin rounded-full border-[var(--color-border)] border-t-[var(--color-accent)] ${sizeStyles[size]}`}
        role="status"
        aria-label="Loading"
      />
      {text && (
        <span className="text-sm text-[var(--color-text-secondary)]">{text}</span>
      )}
    </div>
  );
}
