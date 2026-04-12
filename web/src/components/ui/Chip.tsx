import { X } from 'lucide-react';

interface ChipProps {
  label: string;
  color?: string;
  selected?: boolean;
  onRemove?: () => void;
  onClick?: () => void;
  className?: string;
}

export function Chip({
  label,
  color,
  selected = false,
  onRemove,
  onClick,
  className = '',
}: ChipProps) {
  return (
    <span
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onClick={onClick}
      onKeyDown={onClick ? (e) => e.key === 'Enter' && onClick() : undefined}
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors ${
        selected
          ? 'border-[var(--color-accent)] bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
          : 'border-[var(--color-border)] bg-[var(--color-bg-card)] text-[var(--color-text-primary)]'
      } ${onClick ? 'cursor-pointer hover:border-[var(--color-accent)]' : ''} ${className}`}
    >
      {color && (
        <span
          className="h-2 w-2 rounded-full"
          style={{ backgroundColor: color }}
        />
      )}
      <span>{label}</span>
      {onRemove && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onRemove();
          }}
          className="text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
          aria-label={`Remove ${label}`}
        >
          <X className="h-3 w-3" />
        </button>
      )}
    </span>
  );
}
