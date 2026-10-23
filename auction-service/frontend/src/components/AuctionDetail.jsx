import { useEffect, useRef, useState } from 'react';
import { money } from '../api.js';
import { useBids, useLiveAuction, useWinnerOrder } from '../hooks.js';
import BidForm from './BidForm.jsx';
import BidHistory from './BidHistory.jsx';
import Countdown from './Countdown.jsx';
import OrderTracker from './OrderTracker.jsx';

export default function AuctionDetail({ id, me, onChange }) {
  const auction = useLiveAuction(id);
  const bids = useBids(id, auction ? `${auction.bidCount}-${auction.status}` : '');
  const order = useWinnerOrder(auction);
  const [notice, setNotice] = useState(null);   // banner from a bid result or from being outbid
  const [hint, setHint] = useState(null);       // inline message from the last bid, e.g. "too low"
  const lastLeader = useRef(null);

  // Each pushed update: notice "you've been outbid", clear the last bid's hint, refresh the list on the left.
  useEffect(() => {
    if (!auction) return;
    const prev = lastLeader.current;
    if (prev !== null && prev !== auction.leaderId && prev === me && auction.status === 'OPEN') {
      setNotice({ kind: 'lose', text: `You've been outbid. The price is now ${money(auction.currentPrice)}.` });
    }
    lastLeader.current = auction.leaderId;
    setHint(null);
    onChange();
  }, [auction]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!auction) return <section className="panel detail placeholder"><p>Loading…</p></section>;

  const open = auction.status === 'OPEN';
  let banner = notice;
  if (auction.status === 'SOLD') {
    banner = auction.leaderId === me
      ? { kind: 'win', text: `You won at ${money(auction.currentPrice)}. Follow your order below.` }
      : { kind: 'info', text: `Sold to ${auction.leaderId} for ${money(auction.currentPrice)}.` };
  } else if (auction.status === 'UNSOLD') {
    banner = { kind: 'info', text: 'Ended with no bids.' };
  }

  const defaultHint = auction.leaderId === me
    ? 'You are winning. You can raise your maximum without raising the price.'
    : `Minimum bid: ${money(auction.minimumNextBid)}`;

  function onBid(result) {
    if (result.banner) setNotice({ kind: result.banner, text: result.text });
    setHint(result.hint ? { text: result.hint, error: result.error } : null);
  }

  return (
    <section className="panel detail">
      <div className="top">
        <div>
          <h2>{auction.title}</h2>
          <p className="sub">Item {auction.productId} · sold by {auction.sellerId} · started at {money(auction.startPrice)}</p>
        </div>
        <Countdown auction={auction} />
      </div>
      {banner && <div className={`banner ${banner.kind}`} role="status" aria-live="polite">{banner.text}</div>}
      <div className="price">
        <span className="label">Current price</span>
        <span className="value">{money(auction.currentPrice)}</span>
        <span className="meta">
          {auction.bidCount
            ? `${auction.bidCount} bid${auction.bidCount === 1 ? '' : 's'} · leading: ${auction.leaderId === me ? 'you' : auction.leaderId}`
            : 'No bids yet'}
        </span>
      </div>
      {auction.status === 'SOLD' && <OrderTracker auction={auction} order={order} />}
      {open && <BidForm auction={auction} me={me} onResult={onBid} />}
      {open && <p className={hint?.error ? 'hint err' : 'hint'}>{hint ? hint.text : defaultHint}</p>}
      <p className="explain">Enter the most you would pay. The system bids for you only as much as needed to keep you
        ahead, one increment at a time. Your maximum stays private.</p>
      <BidHistory bids={bids} me={me} />
    </section>
  );
}
