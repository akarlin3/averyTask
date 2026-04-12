import { Tooltip } from '@/components/ui/Tooltip';
import { formatTime } from '@/utils/dates';
import { PRIORITY_CONFIG } from '@/utils/priority';
import type { Task } from '@/types/task';

interface CalendarTaskCardProps {
  task: Task;
  onClick: (task: Task) => void;
  compact?: boolean;
  className?: string;
}

export function CalendarTaskCard({
  task,
  onClick,
  compact = false,
  className = '',
}: CalendarTaskCardProps) {
  const priorityConf = PRIORITY_CONFIG[task.priority];
  const isDone = task.status === 'done';

  return (
    <Tooltip content={task.title} position="top">
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          onClick(task);
        }}
        className={`w-full text-left rounded-md border border-[var(--color-border)] bg-[var(--color-bg-card)] px-2 transition-colors hover:border-[var(--color-accent)]/50 hover:shadow-sm ${
          compact ? 'py-1' : 'py-1.5'
        } ${isDone ? 'opacity-50' : ''} ${className}`}
        draggable={false}
      >
        <div className="flex items-center gap-1.5 min-w-0">
          {/* Priority dot */}
          <span
            className="h-2 w-2 shrink-0 rounded-full"
            style={{ backgroundColor: priorityConf.color }}
          />

          {/* Title */}
          <span
            className={`flex-1 truncate text-xs font-medium ${
              isDone
                ? 'text-[var(--color-text-secondary)] line-through'
                : 'text-[var(--color-text-primary)]'
            }`}
          >
            {task.title}
          </span>

          {/* Time */}
          {task.due_time && !compact && (
            <span className="shrink-0 text-[10px] text-[var(--color-text-secondary)]">
              {formatTime(task.due_time)}
            </span>
          )}
        </div>
      </button>
    </Tooltip>
  );
}
