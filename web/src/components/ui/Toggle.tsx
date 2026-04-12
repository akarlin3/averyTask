type ToggleSize = 'sm' | 'md';

interface ToggleProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  label?: string;
  size?: ToggleSize;
  className?: string;
}

const trackSizes: Record<ToggleSize, string> = {
  sm: 'h-5 w-9',
  md: 'h-6 w-11',
};

const thumbSizes: Record<ToggleSize, { size: string; translate: string }> = {
  sm: { size: 'h-3.5 w-3.5', translate: 'translate-x-4' },
  md: { size: 'h-4.5 w-4.5', translate: 'translate-x-5' },
};

export function Toggle({
  checked,
  onChange,
  disabled = false,
  label,
  size = 'md',
  className = '',
}: ToggleProps) {
  return (
    <label
      className={`inline-flex items-center gap-2 ${disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'} ${className}`}
    >
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => !disabled && onChange(!checked)}
        className={`relative inline-flex shrink-0 items-center rounded-full transition-colors duration-150 ${
          checked ? 'bg-[var(--color-accent)]' : 'bg-[var(--color-border)]'
        } ${trackSizes[size]}`}
      >
        <span
          className={`inline-block rounded-full bg-white shadow-sm transition-transform duration-150 ${
            thumbSizes[size].size
          } ${checked ? thumbSizes[size].translate : 'translate-x-0.5'}`}
        />
      </button>
      {label && (
        <span className="text-sm text-[var(--color-text-primary)]">{label}</span>
      )}
    </label>
  );
}
