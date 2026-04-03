[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/aQj7XJJr)

FEATURES IMPLENTED:
1. OrderBook.java - Core Order Book Data Structure

✅ Price-Time Priority Data Structures:

ConcurrentSkipListMap<Double, List<Order>> bids - Sorted HIGH to LOW
ConcurrentSkipListMap<Double, List<Order>> asks - Sorted LOW to HIGH
Orders at same price level stored as List<Order> for FIFO time priority

✅ Core APIs:

addOrder(Order) - Add to bid/ask side
removeOrder(Order) - Cancel/remove order
getBestBid() - Query top of book (highest bid)
getBestAsk() - Query top of book (lowest ask)
getSpread() - Calculate bid-ask spread
hasMatchableOrders() - Check for crossed book

✅ Concurrency Strategy:

Thread-safe: ConcurrentSkipListMap provides O(log n) lock-free insertion/deletion
O(1) best price lookup via firstKey()
Per-symbol locking via MatchingEngine synchronized blocks

2. MatchingEngine.java - Order Book Manager

✅ Symbol-based order book registry (ConcurrentHashMap<String, OrderBook>)
✅ Per-symbol locking (synchronized (book)) prevents race conditions
✅ Matching algorithms (BUY/SELL against opposite side)
✅ Execution generation with price-time priority

3. MatchingEngineTest.java - Test Coverage

Test scenarios:

Scenario 1: Resting order (no match)
Scenario 2: Aggressive match (full fill)
Scenario 3: Partial fill with price-time priority
Thread safety and concurrency validation
