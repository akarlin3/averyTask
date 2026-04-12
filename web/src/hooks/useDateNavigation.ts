import { useState, useMemo, useCallback } from 'react';
import {
  addWeeks,
  subWeeks,
  addMonths,
  subMonths,
  addDays,
  subDays,
  startOfWeek,
  endOfWeek,
  startOfMonth,
  endOfMonth,
  format,
} from 'date-fns';

type NavigationMode = 'week' | 'month' | 'day';

export function useDateNavigation(mode: NavigationMode) {
  const [currentDate, setCurrentDate] = useState(new Date());

  const goForward = useCallback(() => {
    setCurrentDate((prev) => {
      switch (mode) {
        case 'week':
          return addWeeks(prev, 1);
        case 'month':
          return addMonths(prev, 1);
        case 'day':
          return addDays(prev, 1);
      }
    });
  }, [mode]);

  const goBack = useCallback(() => {
    setCurrentDate((prev) => {
      switch (mode) {
        case 'week':
          return subWeeks(prev, 1);
        case 'month':
          return subMonths(prev, 1);
        case 'day':
          return subDays(prev, 1);
      }
    });
  }, [mode]);

  const goToToday = useCallback(() => {
    setCurrentDate(new Date());
  }, []);

  const goToDate = useCallback((date: Date) => {
    setCurrentDate(date);
  }, []);

  const dateRange = useMemo(() => {
    switch (mode) {
      case 'week':
        return {
          start: startOfWeek(currentDate, { weekStartsOn: 1 }),
          end: endOfWeek(currentDate, { weekStartsOn: 1 }),
        };
      case 'month':
        return {
          start: startOfMonth(currentDate),
          end: endOfMonth(currentDate),
        };
      case 'day':
        return {
          start: currentDate,
          end: currentDate,
        };
    }
  }, [currentDate, mode]);

  const label = useMemo(() => {
    switch (mode) {
      case 'week': {
        const s = startOfWeek(currentDate, { weekStartsOn: 1 });
        const e = endOfWeek(currentDate, { weekStartsOn: 1 });
        if (s.getMonth() === e.getMonth()) {
          return `${format(s, 'MMM d')} - ${format(e, 'd, yyyy')}`;
        }
        if (s.getFullYear() === e.getFullYear()) {
          return `${format(s, 'MMM d')} - ${format(e, 'MMM d, yyyy')}`;
        }
        return `${format(s, 'MMM d, yyyy')} - ${format(e, 'MMM d, yyyy')}`;
      }
      case 'month':
        return format(currentDate, 'MMMM yyyy');
      case 'day':
        return format(currentDate, 'EEEE, MMMM d, yyyy');
    }
  }, [currentDate, mode]);

  return {
    currentDate,
    goForward,
    goBack,
    goToToday,
    goToDate,
    dateRange,
    label,
  };
}
