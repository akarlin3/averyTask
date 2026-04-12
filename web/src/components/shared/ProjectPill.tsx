import { useNavigate } from 'react-router-dom';

interface ProjectPillProps {
  id: number;
  name: string;
  color?: string;
  className?: string;
}

export function ProjectPill({ id, name, color = 'var(--color-accent)', className = '' }: ProjectPillProps) {
  const navigate = useNavigate();

  return (
    <button
      type="button"
      onClick={() => navigate(`/projects/${id}`)}
      className={`inline-flex items-center gap-1.5 rounded-full border border-[var(--color-border)] px-2.5 py-0.5 text-xs font-medium text-[var(--color-text-primary)] hover:bg-[var(--color-bg-secondary)] transition-colors ${className}`}
    >
      <span className="h-2 w-2 rounded-full" style={{ backgroundColor: color }} />
      {name}
    </button>
  );
}
