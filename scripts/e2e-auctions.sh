#!/usr/bin/env bash
# End-to-end check through every service (needs `docker compose up -d`):
#   alice (balance 500) wins auction A -> her order must reach COMPLETED and she pays the second price;
#   bob (balance 10) wins auction B at 20 -> payment fails -> ROLLED_BACK and his reserved stock is released.

set -u
J='-H Content-Type:application/json'
curl -s -X POST localhost:8082/inventory/seed $J -d '{"productId":"camera-1","quantity":5}' >/dev/null
curl -s -X POST localhost:8083/accounts/seed $J -d '{"customerId":"alice","balance":500.00}' >/dev/null
curl -s -X POST localhost:8083/accounts/seed $J -d '{"customerId":"bob","balance":10.00}' >/dev/null
echo "stock before: $(curl -s localhost:8082/inventory/camera-1)"
A=$(curl -s -X POST localhost:8084/auctions $J -d '{"sellerId":"sam","productId":"camera-1","title":"Camera A","startPrice":10.00,"durationSeconds":6}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])')
B=$(curl -s -X POST localhost:8084/auctions $J -d '{"sellerId":"sam","productId":"camera-1","title":"Camera B","startPrice":20.00,"durationSeconds":6}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])')
curl -s -X POST localhost:8084/auctions/$A/bids $J -d '{"idempotencyKey":"a1","bidderId":"alice","maxAmount":50.00}' | python3 -c 'import json,sys;d=json.load(sys.stdin);print("alice bids max 50 on A:",d["outcome"],"price",d["currentPrice"])'
curl -s -X POST localhost:8084/auctions/$B/bids $J -d '{"idempotencyKey":"b1","bidderId":"bob","maxAmount":40.00}' | python3 -c 'import json,sys;d=json.load(sys.stdin);print("bob bids max 40 on B:",d["outcome"],"price",d["currentPrice"])'
echo "waiting for both auctions to end and their orders to settle..."
for id in $A $B; do
  last=""
  for i in $(seq 1 60); do
    r=$(curl -s localhost:8084/auctions/$id/order)
    st=$(echo "$r" | python3 -c 'import json,sys
try:
  d=json.load(sys.stdin); print(d.get("orderStatus") or d.get("state"))
except Exception: print("NOT_SOLD_YET")')
    [ "$st" != "$last" ] && echo "  auction ${id:0:8}: $st" && last=$st
    case "$st" in COMPLETED|ROLLED_BACK|INVENTORY_REJECTED) break;; esac
    sleep 1
  done
done
echo "stock after: $(curl -s localhost:8082/inventory/camera-1)"
echo "alice: $(curl -s localhost:8083/accounts/alice)"
echo "bob:   $(curl -s localhost:8083/accounts/bob)"
