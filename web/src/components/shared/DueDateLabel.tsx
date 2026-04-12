import { Calendar } from 'lucide-react';
import { formatDate, isOverdue } from '@/utils/dates';
import { isToday, isTomorrow, parseISO } from 'date-fns';

interface DueDateLabelProps {
  date: string | null;
  showIcon?: boolean;
  className?: string;
}

export function DueDateLabel({ date, showIcon = true, className = '' }: DueDateLabelProps) {
  if (!date) return null;

  const parsed = parseISO(date);
  const overdue = isOverdue(date);
  const today = isToday(parsed);
  const tomorrow = isTomorrow(parsed);

  let colorClass = 'text-[var(--color-text-secondary)]';
  if (overdue) colorClass = 'text-red-500';
  else if (today) colorClass = 'text-[var(--color-accent)]';
  else if (tomorrow) colorClass = 'text-amber-500';

  return (
    <span className={`inline-flex items-center gap-1 text-xs font-medium ${colorClass} ${className}`}>
      {showIcon && <Calendar className="h-3 w-3" />}
      {formatDate(date)}
    </span>
  );
}
