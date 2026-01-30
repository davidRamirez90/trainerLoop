import type { CoachEvent } from '../types/coach';
import { formatDuration } from './time';

export const buildCoachNotes = (events: CoachEvent[]): string => {
  if (!events.length) {
    return '';
  }

  return events
    .map((event) => {
      const time = formatDuration(event.timestampSec);
      const decision = event.decision && event.kind === 'decision'
        ? ` (${event.decision})`
        : '';
      return `[${time}] ${event.message}${decision}`;
    })
    .join('\n');
};
