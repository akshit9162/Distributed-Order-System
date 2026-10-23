// Auction page: list and create auctions, watch one live, place maximum (proxy) bids, follow the winner's order.
import { useEffect, useState } from 'react';
import { postJson } from './api.js';
import { useOpenAuctions } from './hooks.js';
import AuctionList from './components/AuctionList.jsx';
import CreateAuction from './components/CreateAuction.jsx';
import AuctionDetail from './components/AuctionDetail.jsx';

function initialBidder() {
  try { return localStorage.getItem('bidder') || `bidder-${Math.random().toString(36).slice(2, 6)}`; }
  catch { return `bidder-${Math.random().toString(36).slice(2, 6)}`; }
}

export default function App() {
  const [me, setMe] = useState(initialBidder);
  const [selected, setSelected] = useState(null);
  const [auctions, refresh] = useOpenAuctions();

  useEffect(() => {
    try { localStorage.setItem('bidder', me.trim()); } catch { /* private mode: name just isn't remembered */ }
  }, [me]);

  async function create(form) {
    const { ok, body } = await postJson('/auctions', {
      sellerId: me.trim(), productId: `item-${Date.now().toString(36)}`, title: form.title,
      startPrice: Number(form.startPrice), durationSeconds: Number(form.durationSeconds),
    });
    if (ok) { await refresh(); setSelected(body.id); }
  }

  return (
    <>
      <header>
        <div className="brand">auction<em>service</em></div>
        <label className="me">Bidding as{' '}
          <input value={me} onChange={(e) => setMe(e.target.value)} autoComplete="nickname" maxLength={40} />
        </label>
      </header>
      <main>
        <section className="col">
          <AuctionList auctions={auctions} selected={selected} onOpen={setSelected} />
          <CreateAuction onCreate={create} />
        </section>
        {selected
          // key: switching auctions mounts a fresh detail view, so no state (banners, outbid tracking) leaks across.
          ? <AuctionDetail key={selected} id={selected} me={me.trim()} onChange={refresh} />
          : <section className="panel detail placeholder"><p>Pick an auction on the left, or list an item to start one.</p></section>}
      </main>
    </>
  );
}
