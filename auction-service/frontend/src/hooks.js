import { useCallback, useEffect, useState } from 'react';
import { json } from './api.js';
import { isSettled } from './tracker.js';

// Re-renders every `ms` milliseconds; drives the countdown.
export function useNow(ms) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), ms);
    return () => clearInterval(t);
  }, [ms]);
  return now;
}

// Open auctions, refreshed every 5 s and on demand (after creating one or when a viewed auction changes).
export function useOpenAuctions() {
  const [auctions, setAuctions] = useState([]);
  const refresh = useCallback(async () => {
    const { ok, body } = await json('/auctions');
    if (ok) setAuctions(body);
  }, []);
  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 5000);
    return () => clearInterval(t);
  }, [refresh]);
  return [auctions, refresh];
}

// One auction's public state, kept live over Server-Sent Events. The server pushes it after every accepted bid and
// when the auction closes; EventSource reconnects by itself if the connection drops. It is also fetched once up
// front, so the page never waits for the first push.
export function useLiveAuction(id) {
  const [auction, setAuction] = useState(null);
  useEffect(() => {
    let cancelled = false;
    json(`/auctions/${id}`).then(({ ok, body }) => { if (ok && !cancelled) setAuction((a) => a ?? body); });
    const stream = new EventSource(`/auctions/${id}/stream`);
    stream.addEventListener('auction', (e) => setAuction(JSON.parse(e.data)));
    return () => { cancelled = true; stream.close(); };
  }, [id]);
  return auction;
}

// Bid history, reloaded whenever the auction changes (a new bid or closing).
export function useBids(id, version) {
  const [bids, setBids] = useState([]);
  useEffect(() => {
    let cancelled = false;
    json(`/auctions/${id}/bids`).then(({ ok, body }) => { if (ok && !cancelled) setBids(body); });
    return () => { cancelled = true; };
  }, [id, version]);
  return bids;
}

// The winner's order, polled once a second after the auction is SOLD until the SAGA settles.
export function useWinnerOrder(auction) {
  const [order, setOrder] = useState(null);
  const sold = auction?.status === 'SOLD';
  const id = auction?.id;
  useEffect(() => {
    if (!sold) { setOrder(null); return undefined; }
    let timer = null;
    let cancelled = false;
    const poll = async () => {
      const { ok, body } = await json(`/auctions/${id}/order`);
      if (cancelled || !ok) return;
      setOrder(body);
      if (isSettled(body)) clearInterval(timer);
    };
    poll();
    timer = setInterval(poll, 1000);
    return () => { cancelled = true; clearInterval(timer); };
  }, [id, sold]);
  return order;
}
