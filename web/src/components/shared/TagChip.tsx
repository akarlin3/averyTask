import type { Tag } from '@/types/tag';

interface TagChipProps {
  tag: Tag;
  onRemove?: () => void;
  className?: string;
}

export function TagChip({ tag, onRemove, className = '' }: TagChipProps) {
  const color = tag.color || 'var(--color-accent)';

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border border-[var(--color-border)] px-2 py-0.5 text-xs font-medium text-[var(--color-text-primary)] ${className}`}
    >
      <span className="h-2 w-2 rounded-full" style={{ backgroundColor: color }} />
      {tag.name}
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          className="ml-0.5 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
          aria-label={`Remove ${tag.name}`}
        >
          &times;
        </button>
      )}
    </span>
  );
}
