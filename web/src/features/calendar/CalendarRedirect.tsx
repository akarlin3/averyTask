import { Navigate } from 'react-router-dom';

export function CalendarRedirect() {
  const lastView = localStorage.getItem('prismtask_calendar_view');
  const target = lastView || '/calendar/week';
  return <Navigate to={target} replace />;
}
