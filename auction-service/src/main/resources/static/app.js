// Auction page: list and create auctions, watch one live over Server-Sent Events, place maximum (proxy) bids.
const $ = (id) => document.getElementById(id);
const money = (n) => (n == null ? '–' : Number(n).toLocaleString('en-US', { style: 'currency', currency: 'USD' }));

const me = $('me');
me.value = localStorage.getItem('bidder') || `bidder-${Math.random().toString(36).slice(2, 6)}`;
me.addEventListener('change', () => localStorage.setItem('bidder', me.value.trim()));
localStorage.setItem('bidder', me.value);

let current = null;        // auction being viewed
let stream = null;         // its EventSource
let lastLeader = null;     // to notice "you've been outbid"

async function json(path, options) {
  const r = await fetch(path, options);
  return { ok: r.ok, status: r.status, body: await r.json().catch(() => ({})) };
}

async function loadList() {
  const { body: auctions } = await json('/auctions');
  const ul = $('list');
  ul.replaceChildren();
  if (!auctions.length) {
    const li = document.createElement('li');
    li.className = 'empty';
    li.textContent = 'No open auctions. List one below.';
    ul.append(li);
    return;
  }
  for (const a of auctions) {
    const li = document.createElement('li');
    const b = document.createElement('button');
    b.type = 'button';
    if (current && current.id === a.id) b.className = 'on';
    const t = document.createElement('span');
    t.textContent = a.title;
    const s = document.createElement('small');
    s.textContent = `${money(a.currentPrice)} · ${a.bidCount} bids`;
    b.append(t, s);
    b.onclick = () => open(a.id);
    li.append(b);
    ul.append(li);
  }
}

function open(id) {
  if (stream) stream.close();
  lastLeader = null;
  $('placeholder').hidden = true;
  $('detail').hidden = false;
  $('banner').hidden = true;
  $('hint').textContent = '';
  // Live updates: the server pushes the auction's public state after every accepted bid and when it closes.
  // EventSource reconnects by itself if the connection drops.
  stream = new EventSource(`/auctions/${id}/stream`);
  stream.addEventListener('auction', (e) => render(JSON.parse(e.data)));
  loadHistory(id);
}

function render(a) {
  const me_ = me.value.trim();
  const leaderChanged = lastLeader !== null && lastLeader !== a.leaderId;
  if (leaderChanged && lastLeader === me_ && a.status === 'OPEN') banner('lose', `You've been outbid. The price is now ${money(a.currentPrice)}.`);
  lastLeader = a.leaderId;
  current = a;

  $('title').textContent = a.title;
  $('sub').textContent = `Item ${a.productId} · sold by ${a.sellerId} · started at ${money(a.startPrice)}`;
  $('price').textContent = money(a.currentPrice);
  $('meta').textContent = a.bidCount
    ? `${a.bidCount} bid${a.bidCount === 1 ? '' : 's'} · leading: ${a.leaderId === me_ ? 'you' : a.leaderId}`
    : 'No bids yet';

  const open_ = a.status === 'OPEN';
  $('bid').hidden = !open_;
  if (open_) {
    const input = $('bid').elements.maxAmount;
    input.min = a.minimumNextBid;
    if (!input.value || Number(input.value) < a.minimumNextBid) input.placeholder = `${a.minimumNextBid} or more`;
    $('hint').classList.remove('err');
    $('hint').textContent = a.leaderId === me_ ? 'You are winning. You can raise your maximum without raising the price.' : `Minimum bid: ${money(a.minimumNextBid)}`;
  } else if (a.status === 'SOLD') {
    banner(a.leaderId === me_ ? 'win' : 'info', a.leaderId === me_
      ? `You won at ${money(a.currentPrice)}. Your order has been sent for payment.`
      : `Sold to ${a.leaderId} for ${money(a.currentPrice)}.`);
    $('hint').textContent = '';
  } else {
    banner('info', 'Ended with no bids.');
    $('hint').textContent = '';
  }
  loadHistory(a.id);
  loadList();
}

function banner(kind, text) {
  const b = $('banner');
  b.className = `banner ${kind}`;
  b.textContent = text;
  b.hidden = false;
}

async function loadHistory(id) {
  const { body: bids } = await json(`/auctions/${id}/bids`);
  const tbody = $('history');
  tbody.replaceChildren();
  for (const b of bids) {
    const tr = document.createElement('tr');
    for (const v of [b.sequence, b.bidderId === me.value.trim() ? `${b.bidderId} (you)` : b.bidderId, money(b.priceAfter),
      b.outcome === 'OUTBID' ? 'outbid by proxy' : b.outcome === 'MAX_RAISED' ? 'raised maximum' : 'took the lead']) {
      const td = document.createElement('td');
      td.textContent = v;
      tr.append(td);
    }
    tbody.append(tr);
  }
}

function tick() {
  if (!current) return;
  const t = $('timer');
  const left = Math.max(0, Math.round((new Date(current.endsAt) - Date.now()) / 1000));
  if (current.status !== 'OPEN') { t.textContent = 'Ended'; t.className = 'timer ended'; return; }
  const h = Math.floor(left / 3600), m = Math.floor((left % 3600) / 60), s = left % 60;
  t.textContent = left === 0 ? 'Closing…' : `${h ? `${h}h ` : ''}${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  t.className = left <= 10 ? 'timer soon' : 'timer';
}

$('bid').addEventListener('submit', async (e) => {
  e.preventDefault();
  const btn = $('bidbtn');
  btn.disabled = true;
  const maxAmount = Number(e.target.elements.maxAmount.value);
  const key = `${me.value.trim()}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;   // safe to retry
  try {
    const { status, body } = await json(`/auctions/${current.id}/bids`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ idempotencyKey: key, bidderId: me.value.trim(), maxAmount }),
    });
    const hint = $('hint');
    hint.classList.toggle('err', status !== 200);
    if (body.outcome === 'LEADING') banner('win', `You're the highest bidder at ${money(body.currentPrice)}.`);
    else if (body.outcome === 'MAX_RAISED') banner('win', `Maximum raised. You're still winning at ${money(body.currentPrice)}.`);
    else if (body.outcome === 'OUTBID') banner('lose', `Another bidder's maximum was higher. Price is now ${money(body.currentPrice)}.`);
    else if (body.outcome === 'TOO_LOW') hint.textContent = `Too low: bid at least ${money(body.minimumNextBid)}.`;
    else if (body.outcome === 'AUCTION_CLOSED') hint.textContent = 'This auction has ended.';
    else if (body.outcome === 'OWN_AUCTION') hint.textContent = "You can't bid on your own listing.";
    else hint.textContent = body.error || 'Something went wrong.';
    if (status === 200) e.target.reset();
  } finally {
    btn.disabled = false;
  }
});

$('create').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = Object.fromEntries(new FormData(e.target));
  const { ok, body } = await json('/auctions', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      sellerId: me.value.trim(), productId: `item-${Date.now().toString(36)}`, title: f.title,
      startPrice: Number(f.startPrice), durationSeconds: Number(f.durationSeconds),
    }),
  });
  if (ok) { await loadList(); open(body.id); }
});

loadList();
setInterval(loadList, 5000);
setInterval(tick, 250);
