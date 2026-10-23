import { money } from '../api.js';

export default function AuctionList({ auctions, selected, onOpen }) {
  return (
    <div className="panel">
      <h2>Open auctions</h2>
      <ul className="list">
        {auctions.length === 0 && <li className="empty">No open auctions. List one below.</li>}
        {auctions.map((a) => (
          <li key={a.id}>
            <button type="button" className={a.id === selected ? 'on' : undefined} onClick={() => onOpen(a.id)}>
              <span>{a.title}</span>
              <small>{money(a.currentPrice)} · {a.bidCount} bids</small>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
