import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AuctionDetail from '../components/AuctionDetail.jsx';

// A fake EventSource the test can push server events through.
class FakeStream {
  static last = null;
  constructor(url) { this.url = url; this.listeners = {}; this.closed = false; FakeStream.last = this; }
  addEventListener(type, fn) { this.listeners[type] = fn; }
  close() { this.closed = true; }
  push(data) { act(() => this.listeners.auction({ data: JSON.stringify(data) })); }
}

const base = {
  id: 'a1', title: 'Leica M6', productId: 'camera-1', sellerId: 'sam', startPrice: 10, currentPrice: 10,
  minimumNextBid: 10, bidCount: 0, leaderId: null, status: 'OPEN', endsAt: new Date(Date.now() + 60_000).toISOString(),
};

function mockFetch(routes) {
  return vi.fn(async (path, options) => {
    const key = `${options?.method || 'GET'} ${path}`;
    const [status, body] = routes[key] || [200, []];
    return { ok: status === 200, status, json: async () => body };
  });
}

beforeEach(() => { vi.stubGlobal('EventSource', FakeStream); });
afterEach(() => { vi.unstubAllGlobals(); });

describe('AuctionDetail', () => {
  it('loads the auction, then follows live updates over SSE', async () => {
    vi.stubGlobal('fetch', mockFetch({ 'GET /auctions/a1': [200, base] }));
    render(<AuctionDetail id="a1" me="alice" onChange={() => {}} />);
    expect(await screen.findByText('Leica M6')).toBeInTheDocument();
    expect(FakeStream.last.url).toBe('/auctions/a1/stream');

    FakeStream.last.push({ ...base, currentPrice: 18.5, bidCount: 2, leaderId: 'carol', minimumNextBid: 19 });
    expect(screen.getByText('$18.50')).toBeInTheDocument();
    expect(screen.getByText('2 bids · leading: carol')).toBeInTheDocument();
  });

  it("tells the leader when they've been outbid", async () => {
    vi.stubGlobal('fetch', mockFetch({ 'GET /auctions/a1': [200, { ...base, bidCount: 1, leaderId: 'alice' }] }));
    render(<AuctionDetail id="a1" me="alice" onChange={() => {}} />);
    await screen.findByText('1 bid · leading: you');

    FakeStream.last.push({ ...base, currentPrice: 21, bidCount: 2, leaderId: 'bob', minimumNextBid: 21.5 });
    expect(screen.getByRole('status')).toHaveTextContent("You've been outbid. The price is now $21.00.");
  });

  it('shows the new minimum when another bid got in first', async () => {
    const fetchMock = mockFetch({
      'GET /auctions/a1': [200, { ...base, bidCount: 1, leaderId: 'bob', minimumNextBid: 10.5 }],
      'POST /auctions/a1/bids': [422, { outcome: 'TOO_LOW', minimumNextBid: 11 }],
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<AuctionDetail id="a1" me="alice" onChange={() => {}} />);
    await userEvent.type(await screen.findByLabelText(/your maximum bid/i), '10.50');
    await userEvent.click(screen.getByRole('button', { name: 'Place bid' }));

    expect(await screen.findByText('Too low: bid at least $11.00.')).toHaveClass('err');
    const [, options] = fetchMock.mock.calls.find(([p, o]) => o?.method === 'POST');
    const sent = JSON.parse(options.body);
    expect(sent).toMatchObject({ bidderId: 'alice', maxAmount: 10.5 });
    expect(sent.idempotencyKey).toMatch(/^alice-/);
  });

  it('follows the winner’s order once the auction is sold', async () => {
    vi.stubGlobal('fetch', mockFetch({
      'GET /auctions/a1': [200, { ...base, status: 'SOLD', bidCount: 3, leaderId: 'alice', currentPrice: 26 }],
      'GET /auctions/a1/order': [200, { state: 'FOUND', orderStatus: 'COMPLETED', orderId: '22de3a57-1111' }],
    }));
    render(<AuctionDetail id="a1" me="alice" onChange={() => {}} />);
    expect(await screen.findByText('You won at $26.00. Follow your order below.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Done: order 22de3a57 completed for alice at $26.00.')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Place bid' })).not.toBeInTheDocument();
  });

  it('closes the live stream when the view is left', async () => {
    vi.stubGlobal('fetch', mockFetch({ 'GET /auctions/a1': [200, base] }));
    const { unmount } = render(<AuctionDetail id="a1" me="alice" onChange={() => {}} />);
    await screen.findByText('Leica M6');
    const stream = FakeStream.last;
    unmount();
    expect(stream.closed).toBe(true);
  });
});
