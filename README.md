[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/aQj7XJJr)
---

## Features Implemented

### OrderBook.java - Core Order Book Data Structure

#### ✅ Price-Time Priority Data Structures
- **Bids (Buy Orders):** `ConcurrentSkipListMap<Double, List<Order>>` - Sorted HIGH to LOW
  - Best BID = Highest price a buyer is willing to pay (accessed via `firstKey()`)
- **Asks (Sell Orders):** `ConcurrentSkipListMap<Double, List<Order>>` - Sorted LOW to HIGH
  - Best ASK = Lowest price a seller is willing to accept (accessed via `firstKey()`)
- **Time Priority:** Orders at the same price level stored as `List<Order>` for FIFO (first-in, first-out) matching

#### ✅ Core APIs

| API | Purpose |
|-----|---------|
| `addOrder(Order order)` | Add order to bid/ask side at appropriate price level |
| `removeOrder(Order order)` | Cancel/remove order from book (on cancellation or fill) |
| `getBestBid()` | Query highest bid price - O(1) lookup via `firstKey()` |
| `getBestAsk()` | Query lowest ask price - O(1) lookup via `firstKey()` |
| `getSpread()` | Calculate bid-ask spread (ask - bid) |
| `hasMatchableOrders()` | Check if book has crossed orders (bid >= ask) |
| `getTotalBidOrders()` | Count total buy orders across all price levels |
| `getTotalAskOrders()` | Count total sell orders across all price levels |
| `printSnapshot()` | Human-readable order book visualization for debugging |

#### ✅ Concurrency Strategy

| Aspect | Implementation |
|--------|-----------------|
| **Thread Safety** | `ConcurrentSkipListMap` provides lock-free concurrent access |
| **Insertion/Deletion** | O(log n) complexity for price-level sorting |
| **Best Price Lookup** | O(1) constant time access via `firstKey()` |
| **Symbol-Level Locking** | Per-symbol synchronization in `MatchingEngine` prevents race conditions |
| **High-Volume Ready** | Supports concurrent orders from multiple threads without blocking |

---

### MatchingEngine.java - Order Book Manager

#### ✅ Symbol-Based Order Book Registry
- Maintains `ConcurrentHashMap<String, OrderBook>` for multi-symbol trading
- Lazy initialization: Order books created on first order for each symbol
- Supports unlimited symbols with no pre-allocation overhead

#### ✅ Per-Symbol Locking Strategy
- `synchronized (book)` blocks ensure atomic matching operations
- Prevents race conditions when multiple orders arrive for same symbol
- Allows concurrent matching for different symbols

#### ✅ Matching Algorithms
- **BUY orders:** Match against ASK side (lowest prices first - best for buyer)
- **SELL orders:** Match against BID side (highest prices first - best for seller)
- **Price-Time Priority:** Orders matched in price order, then FIFO at same price
- Trade execution at resting order price (not aggressive order price)

#### ✅ Execution Generation
- Creates `Execution` objects for each matched trade
- Updates order status (NEW → PARTIALLY_FILLED → FILLED)
- Tracks cumulative quantity (cumQty) and leaves quantity (leavesQty)
- Maintains weighted average price (avgPx) per order

---

### MatchingEngineTest.java - Test Coverage

#### ✅ Test Scenarios

**Scenario 1: Resting Order (No Match)**
- Send Sell 100 @ $100.00 for MSFT
- Expected: Order added to book, no trades, status remains NEW
- Validates: Order book storage and NEW status assignment

**Scenario 2: Aggressive Match (Full Fill)**
- Resting Sell 100 @ $100.00 (already on book from Scenario 1)
- Send Buy 100 @ $101.00 (aggressive buyer)
- Expected: Trade 100 @ $100.00 (resting order price, not aggressive price)
- Validates: Price-time priority and full fill execution

**Scenario 3: Partial Fill with Price-Time Priority**
- Resting Sell 100 @ $100.00 (Order A)
- Resting Sell 100 @ $101.00 (Order B - higher price)
- Send Buy 150 @ $102.00 (aggressive buyer)
- Expected Trades:
  - Trade 1: 100 @ $100.00 (Order A - better price for buyer)
  - Trade 2: 50 @ $101.00 (Order B - partial match)
  - Remaining: 50 @ $102.00 on book (resting buy order)
- Validates: Price-time priority matching and partial fills

#### ✅ Concurrency Validation
- Thread safety under concurrent order submissions
- Lock-free best price lookup performance
- Order book integrity across multiple symbol streams

---
