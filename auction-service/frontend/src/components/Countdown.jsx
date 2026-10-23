import { remaining } from '../api.js';
import { useNow } from '../hooks.js';

export default function Countdown({ auction }) {
  const now = useNow(250);
  if (auction.status !== 'OPEN') return <div className="timer ended">Ended</div>;
  const { text, soon } = remaining(auction.endsAt, now);
  return <div className={soon ? 'timer soon' : 'timer'} aria-live="off">{text}</div>;
}
