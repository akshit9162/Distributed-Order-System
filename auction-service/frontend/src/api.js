// Small helpers shared by the components: one fetch wrapper and money formatting.
export async function json(path, options) {
  const r = await fetch(path, options);
  return { ok: r.ok, status: r.status, body: await r.json().catch(() => ({})) };
}

export const money = (n) => (n == null ? '–' : Number(n).toLocaleString('en-US', { style: 'currency', currency: 'USD' }));

export function postJson(path, body) {
  return json(path, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
}

// What the bidder sees after the server answers a bid. Pure, so it can be tested without a server.
export function bidMessage(status, body) {
  switch (body.outcome) {
    case 'LEADING': return { banner: 'win', text: `You're the highest bidder at ${money(body.currentPrice)}.` };
    case 'MAX_RAISED': return { banner: 'win', text: `Maximum raised. You're still winning at ${money(body.currentPrice)}.` };
    case 'OUTBID': return { banner: 'lose', text: `Another bidder's maximum was higher. Price is now ${money(body.currentPrice)}.` };
    case 'TOO_LOW': return { hint: `Too low: bid at least ${money(body.minimumNextBid)}.` };
    case 'AUCTION_CLOSED': return { hint: 'This auction has ended.' };
    case 'OWN_AUCTION': return { hint: "You can't bid on your own listing." };
    default: return { hint: body.error || (status === 200 ? '' : 'Something went wrong.') };
  }
}

// Countdown text for an auction ending at endsAt (ISO string), as seen at time now (ms).
export function remaining(endsAt, now) {
  const left = Math.max(0, Math.round((new Date(endsAt) - now) / 1000));
  if (left === 0) return { text: 'Closing…', soon: true };
  const h = Math.floor(left / 3600), m = Math.floor((left % 3600) / 60), s = left % 60;
  return { text: `${h ? `${h}h ` : ''}${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`, soon: left <= 10 };
}
