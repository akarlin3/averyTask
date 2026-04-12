import { AlertTriangle, ArrowUp, Minus, ArrowDown } from 'lucide-react';
import type { TaskPriority } from '@/types/task';
import { PRIORITY_CONFIG } from '@/utils/priority';

interface PriorityBadgeProps {
  priority: TaskPriority;
  iconOnly?: boolean;
  className?: string;
}

const priorityIcons: Record<TaskPriority, typeof AlertTriangle> = {
  1: AlertTriangle,
  2: ArrowUp,
  3: Minus,
  4: ArrowDown,
};

export function PriorityBadge({ priority, iconOnly = false, className = '' }: PriorityBadgeProps) {
  const config = PRIORITY_CONFIG[priority];
  if (!config) return null;

  const Icon = priorityIcons[priority];

  if (iconOnly) {
    return (
      <span title={config.label} className={className}>
        <Icon className="h-4 w-4" style={{ color: config.color }} />
      </span>
    );
  }

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${className}`}
      style={{ color: config.color, backgroundColor: config.bgColor }}
    >
      <Icon className="h-3 w-3" />
      {config.label}
    </span>
  );
}
