import { money } from '../api.js';

const RESULT = { OUTBID: 'outbid by proxy', MAX_RAISED: 'raised maximum' };

export default function BidHistory({ bids, me }) {
  return (
    <>
      <h3>Bid history</h3>
      <table>
        <thead><tr><th>#</th><th>Bidder</th><th>Price after</th><th>Result</th></tr></thead>
        <tbody>
          {bids.map((b) => (
            <tr key={b.sequence}>
              <td>{b.sequence}</td>
              <td>{b.bidderId === me ? `${b.bidderId} (you)` : b.bidderId}</td>
              <td>{money(b.priceAfter)}</td>
              <td>{RESULT[b.outcome] || 'took the lead'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
