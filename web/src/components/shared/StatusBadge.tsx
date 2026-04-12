import { Circle, Clock, CheckCircle2, XCircle } from 'lucide-react';
import type { TaskStatus } from '@/types/task';

interface StatusBadgeProps {
  status: TaskStatus;
  className?: string;
}

const statusConfig: Record<TaskStatus, { label: string; color: string; bgColor: string; icon: typeof Circle }> = {
  todo: {
    label: 'To Do',
    color: 'var(--color-text-secondary)',
    bgColor: 'rgba(107, 114, 128, 0.1)',
    icon: Circle,
  },
  in_progress: {
    label: 'In Progress',
    color: '#3b82f6',
    bgColor: 'rgba(59, 130, 246, 0.1)',
    icon: Clock,
  },
  done: {
    label: 'Done',
    color: '#22c55e',
    bgColor: 'rgba(34, 197, 94, 0.1)',
    icon: CheckCircle2,
  },
  cancelled: {
    label: 'Cancelled',
    color: '#ef4444',
    bgColor: 'rgba(239, 68, 68, 0.1)',
    icon: XCircle,
  },
};

export function StatusBadge({ status, className = '' }: StatusBadgeProps) {
  const config = statusConfig[status];
  if (!config) return null;

  const Icon = config.icon;

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${
        status === 'cancelled' ? 'line-through' : ''
      } ${className}`}
      style={{ color: config.color, backgroundColor: config.bgColor }}
    >
      <Icon className="h-3 w-3" />
      {config.label}
    </span>
  );
}
