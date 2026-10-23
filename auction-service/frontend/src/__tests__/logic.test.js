import { describe, expect, it } from 'vitest';
import { bidMessage, remaining } from '../api.js';
import { isSettled, trackerView } from '../tracker.js';

describe('bidMessage', () => {
  it('turns each outcome into a banner or an inline hint', () => {
    expect(bidMessage(200, { outcome: 'LEADING', currentPrice: 12.5 })).toEqual({ banner: 'win', text: "You're the highest bidder at $12.50." });
    expect(bidMessage(200, { outcome: 'OUTBID', currentPrice: 20 }).banner).toBe('lose');
    expect(bidMessage(422, { outcome: 'TOO_LOW', minimumNextBid: 10.5 })).toEqual({ hint: 'Too low: bid at least $10.50.' });
    expect(bidMessage(403, { outcome: 'OWN_AUCTION' }).hint).toMatch(/own listing/);
    expect(bidMessage(500, {}).hint).toBe('Something went wrong.');
  });
});

describe('remaining', () => {
  const end = '2026-10-23T12:00:00Z';
  it('formats minutes and seconds, flags the last ten seconds', () => {
    expect(remaining(end, Date.parse('2026-10-23T11:58:55Z'))).toEqual({ text: '01:05', soon: false });
    expect(remaining(end, Date.parse('2026-10-23T11:59:52Z'))).toEqual({ text: '00:08', soon: true });
    expect(remaining(end, Date.parse('2026-10-23T09:59:00Z')).text).toBe('2h 01:00');
    expect(remaining(end, Date.parse('2026-10-23T12:00:05Z')).text).toBe('Closing…');
  });
});

describe('trackerView', () => {
  const auction = { leaderId: 'alice', currentPrice: 26 };
  it('walks the SAGA steps', () => {
    expect(trackerView(auction, { state: 'NOT_CREATED_YET' }).marks).toEqual(['done', 'active', '', '', '']);
    expect(trackerView(auction, { state: 'FOUND', orderStatus: 'INVENTORY_RESERVED' }).marks).toEqual(['done', 'done', 'done', 'active', '']);
    const done = trackerView(auction, { state: 'FOUND', orderStatus: 'COMPLETED', orderId: '22de3a57-xxxx' });
    expect(done.marks.every((m) => m === 'done')).toBe(true);
    expect(done.note).toBe('Done: order 22de3a57 completed for alice at $26.00.');
  });
  it('shows a failed payment as compensated', () => {
    const v = trackerView(auction, { state: 'FOUND', orderStatus: 'ROLLED_BACK' });
    expect(v.marks[3]).toBe('failed');
    expect(v.note).toMatch(/compensating transaction/);
  });
  it('knows when to stop polling', () => {
    expect(isSettled({ state: 'FOUND', orderStatus: 'COMPLETED' })).toBe(true);
    expect(isSettled({ state: 'FOUND', orderStatus: 'PENDING' })).toBe(false);
    expect(isSettled({ state: 'NOT_CREATED_YET' })).toBe(false);
  });
});
