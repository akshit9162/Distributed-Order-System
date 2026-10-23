import { STEPS, trackerView } from '../tracker.js';

export default function OrderTracker({ auction, order }) {
  const { marks, note } = trackerView(auction, order);
  return (
    <div className="tracker">
      <h3>Order for the winner</h3>
      <ol className="steps">
        {STEPS.map((label, i) => <li key={label} className={marks[i] || undefined}>{label}</li>)}
      </ol>
      <p className="tracker-note" role="status" aria-live="polite">{note}</p>
    </div>
  );
}
