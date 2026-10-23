import { useState } from 'react';
import { bidMessage, postJson } from '../api.js';

// Places a maximum bid. Each submission gets a fresh idempotency key, so a retried request is applied once.
export default function BidForm({ auction, me, onResult }) {
  const [amount, setAmount] = useState('');
  const [pending, setPending] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setPending(true);
    try {
      const { status, body } = await postJson(`/auctions/${auction.id}/bids`, {
        idempotencyKey: `${me}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        bidderId: me,
        maxAmount: Number(amount),
      });
      onResult({ ...bidMessage(status, body), error: status !== 200 });
      if (status === 200) setAmount('');
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="bidform" onSubmit={submit}>
      <label>Your maximum bid
        <input name="maxAmount" type="number" min={auction.minimumNextBid} step="0.01" required inputMode="decimal"
          placeholder={`${auction.minimumNextBid} or more`} value={amount} onChange={(e) => setAmount(e.target.value)} />
      </label>
      <button type="submit" disabled={pending}>Place bid</button>
    </form>
  );
}
