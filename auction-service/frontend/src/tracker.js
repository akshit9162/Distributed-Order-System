// The winner's purchase runs the order SAGA in other services: order created -> inventory reserved -> payment
// -> completed, or compensated if a step fails. auction-service relays its status; this maps it to the tracker.
import { money } from './api.js';

export const STEPS = ['Won at auction', 'Order created', 'Stock reserved', 'Payment taken', 'Completed'];
export const FINAL = new Set(['COMPLETED', 'INVENTORY_REJECTED', 'ROLLED_BACK']);

const MARKS = {
  PENDING: ['done', 'done', 'active', '', ''],
  INVENTORY_RESERVED: ['done', 'done', 'done', 'active', ''],
  PAYMENT_CONFIRMED: ['done', 'done', 'done', 'done', 'active'],
  COMPLETED: ['done', 'done', 'done', 'done', 'done'],
  INVENTORY_REJECTED: ['done', 'done', 'failed', '', ''],
  PAYMENT_REJECTED: ['done', 'done', 'done', 'failed', ''],
  ROLLING_BACK: ['done', 'done', 'done', 'failed', ''],
  ROLLED_BACK: ['done', 'done', 'done', 'failed', ''],
};

const NOTES = {
  PENDING: 'Order created. Reserving stock…',
  INVENTORY_RESERVED: 'Stock reserved. Taking payment…',
  PAYMENT_CONFIRMED: 'Payment taken. Finishing…',
  INVENTORY_REJECTED: 'Out of stock: the order was rejected before any payment.',
  PAYMENT_REJECTED: 'Payment failed. Releasing the reserved stock…',
  ROLLING_BACK: 'Payment failed. Releasing the reserved stock…',
  ROLLED_BACK: 'Payment failed, so the reserved stock was released (compensating transaction).',
};

// marks: one of done | active | failed | '' (not reached) per step.
export function trackerView(auction, t) {
  if (!t) return { marks: ['done', 'active', '', '', ''], note: '' };
  if (t.state === 'ORDER_SERVICE_UNAVAILABLE') {
    return { marks: ['done', 'active', '', '', ''],
      note: 'Order service not reachable (running the auction service on its own). The win is saved and will be sent when it is.' };
  }
  if (t.state === 'NOT_CREATED_YET') {
    return { marks: ['done', 'active', '', '', ''], note: 'Win sent to the order service through Kafka. Waiting for the order…' };
  }
  const s = t.orderStatus;
  const note = s === 'COMPLETED'
    ? `Done: order ${String(t.orderId).slice(0, 8)} completed for ${auction.leaderId} at ${money(auction.currentPrice)}.`
    : NOTES[s] || s;
  return { marks: MARKS[s] || ['done', 'active', '', '', ''], note };
}

export const isSettled = (t) => t?.state === 'FOUND' && FINAL.has(t.orderStatus);
