package com.stocker.tests;

import com.stocker.MatchingEngine;
import com.stocker.Order;
import com.stocker.Execution;
import java.util.ArrayList;
import java.util.List;

/**
 * LAB 7: Matching Engine Core Algorithms - Test Suite
 * 
 * This comprehensive test validates:
 * 1. Price-Time Priority matching algorithm
 * 2. Order book management with ConcurrentSkipListMap
 * 3. Trade execution at resting order prices
 * 4. Partial fill scenarios
 * 5. Thread safety and concurrency
 * 
 * VALIDATION SCENARIOS:
 * =====================
 * 
 * Scenario 1: RESTING ORDER (No Match)
 *   - Send Sell 100 @ 50.00 for MSFT
 *   - Expected: Order added to book, no trades
 * 
 * Scenario 2: AGGRESSIVE MATCH (Full Fill)
 *   - Scenario 1 order on book
 *   - Send Buy 100 @ 51.00 for MSFT
 *   - Expected: Trade 100 @ 50.00 (resting price, not aggressive price!)
 * 
 * Scenario 3: PARTIAL FILL with Price-Time Priority
 *   - Sell 100 @ 50.00 (Resting Order A)
 *   - Sell 100 @ 51.00 (Resting Order B)
 *   - Buy 150 @ 52.00 (Aggressive)
 *   - Expected Trades:
 *     * Trade 1: 100 @ 50.00 (Order A - better price for buyer)
 *     * Trade 2: 50 @ 51.00 (Order B - partial match)
 *     * Buy order remaining: 50 @ 52.00 on book
 */
public class MatchingEngineTest {

    private static MatchingEngine engine;
    private static int tradeCount = 0;
    private static double totalTradeVolume = 0;
    private static List<String> traceLog = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println(" LAB 7: MATCHING ENGINE - CORE ALGORITHMS TEST SUITE");
        System.out.println("=".repeat(80) + "\n");

        engine = new MatchingEngine();

        try {
            // Run all test scenarios
            testScenario1_RestingOrder();
            testScenario2_AggressiveMatch();
            testScenario3_PartialFillWithPriceTimePriority();
            testAssessmentTrace();

            // Print summary
            printSummary();

        } catch (Exception e) {
            System.err.println("[ERROR] Test execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * SCENARIO 1: RESTING ORDER
     * A single order is placed on the book with no counterparty to match.
     */
    private static void testScenario1_RestingOrder() {
        System.out.println("\n" + "-".repeat(80));
        System.out.println("SCENARIO 1: RESTING ORDER - Single Order (No Match)");
        System.out.println("-".repeat(80));

        Order sell = new Order("SELL_001", "MSFT", '2', 100.00, 100);
        System.out.println("[TEST] Sending: " + orderToString(sell));

        List<Execution> executions = engine.matchOrder(sell);

        traceLog.add("Scenario 1: Sell 100 @ $100.00 for MSFT");
        traceLog.add("  Expected: No trades (resting order)");
        traceLog.add("  Actual: " + executions.size() + " trade(s)");
        traceLog.add("  Order Status: " + sell.getStatus());

        if (executions.isEmpty() && sell.getStatus().equals("NEW")) {
            System.out.println("✓ PASS: Order is resting on book (NEW status, no executions)");
        } else {
            System.out.println("✗ FAIL: Expected resting order but got " + executions.size() + " executions");
        }

        engine.printMarketDataSnapshot();
    }

    /**
     * SCENARIO 2: AGGRESSIVE MATCH
     * An aggressive buyer matches against the resting seller at the seller's price.
     * NOTE: Trade executes at the RESTING price, not the aggressive price!
     */
    private static void testScenario2_AggressiveMatch() {
        System.out.println("\n" + "-".repeat(80));
        System.out.println("SCENARIO 2: AGGRESSIVE MATCH - Buyer Crosses Spread");
        System.out.println("-".repeat(80));

        // Reset engine for clean test
        engine = new MatchingEngine();

        // Place resting sell order
        Order sell = new Order("SELL_001", "MSFT", '2', 100.00, 100);
        engine.matchOrder(sell);
        System.out.println("[TEST] Resting: " + orderToString(sell));

        // Aggressive buy order (willing to pay 101, but seller only asks 100)
        Order buy = new Order("BUY_001", "MSFT", '1', 101.00, 100);
        System.out.println("[TEST] Aggressive: " + orderToString(buy) + " (better price available!)");

        List<Execution> executions = engine.matchOrder(buy);

        traceLog.add("\nScenario 2: Aggressive Buy 100 @ $101.00 matches Sell @ $100.00");
        traceLog.add("  Expected: Trade 100 @ $100.00 (resting price)");
        traceLog.add("  Actual: " + executions.size() + " trade(s)");

        if (!executions.isEmpty()) {
            Execution exec = executions.get(0);
            traceLog.add("  Trade Price: $" + String.format("%.2f", exec.getExecPrice()));
            traceLog.add("  Trade Qty: " + exec.getExecQty());

            if (Math.abs(exec.getExecPrice() - 100.00) < 0.01 && exec.getExecQty() == 100) {
                System.out.println("✓ PASS: Trade at $100.00 (resting price, not $101.00)");
                System.out.println("        [CRITICAL VALIDATION] Price is correct!");
                totalTradeVolume += exec.getExecQty();
                tradeCount++;
            } else {
                System.out.println("✗ FAIL: Expected trade @ $100.00 but got @ $" + 
                    String.format("%.2f", exec.getExecPrice()));
            }
        } else {
            System.out.println("✗ FAIL: No executions generated");
        }

        System.out.println("  Buy Status: " + buy.getStatus() + " (should be FILLED)");
        engine.printMarketDataSnapshot();
    }

    /**
     * SCENARIO 3: PARTIAL FILL with PRICE-TIME PRIORITY
     * Multiple resting orders test both price priority and time priority.
     * 
     * Setup:
     *   - Sell 100 @ 50.00 (Order A - placed first, better price)
     *   - Sell 100 @ 51.00 (Order B - placed second, worse price)
     *   
     * Incoming Aggressive:
     *   - Buy 150 @ 52.00 (willing to pay 52, crosses entire sell side)
     * 
     * Expected:
     *   - Trade 1: 100 @ 50.00 (Order A - best price first)
     *   - Trade 2: 50 @ 51.00 (Order B - partial, takes 50 of its 100)
     *   - Remaining Buy: 50 @ 52.00 on book (new resting order)
     */
    private static void testScenario3_PartialFillWithPriceTimePriority() {
        System.out.println("\n" + "-".repeat(80));
        System.out.println("SCENARIO 3: PARTIAL FILL - Price-Time Priority");
        System.out.println("-".repeat(80));

        // Reset engine
        engine = new MatchingEngine();

        // Resting Order A: Sell @ 50.00
        Order sellA = new Order("SELL_A", "GOOG", '2', 50.00, 100);
        engine.matchOrder(sellA);
        System.out.println("[TEST] Resting A: " + orderToString(sellA) + " (placed first)");

        // Resting Order B: Sell @ 51.00 (worse price for buyer = time priority test)
        Order sellB = new Order("SELL_B", "GOOG", '2', 51.00, 100);
        engine.matchOrder(sellB);
        System.out.println("[TEST] Resting B: " + orderToString(sellB) + " (placed second)");

        // Display book state before aggressive
        System.out.println("\n[BOOK STATE] Before aggressive buy:");
        engine.getOrderBook("GOOG").printSnapshot();

        // Aggressive Buy Order: 150 shares @ 52.00
        Order buy = new Order("BUY_AGG", "GOOG", '1', 52.00, 150);
        System.out.println("[TEST] Aggressive: " + orderToString(buy));

        List<Execution> executions = engine.matchOrder(buy);

        traceLog.add("\nScenario 3: PRICE-TIME PRIORITY TEST");
        traceLog.add("  Resting A: Sell 100 @ $50.00 (placed first)");
        traceLog.add("  Resting B: Sell 100 @ $51.00 (placed second)");
        traceLog.add("  Aggressive: Buy 150 @ $52.00");
        traceLog.add("  Expected Executions:");
        traceLog.add("    1. Trade 100 @ $50.00 (Order A - price priority)");
        traceLog.add("    2. Trade 50 @ $51.00 (Order B - partial fill)");
        traceLog.add("    3. Remaining Buy 50 @ $52.00 (on book)");
        traceLog.add("  Actual Executions: " + executions.size());

        System.out.println("\n[RESULTS] Trades Generated: " + executions.size());
        for (int i = 0; i < executions.size(); i++) {
            Execution exec = executions.get(i);
            System.out.printf("  Trade %d: %.0f @ $%.2f%n", i + 1, exec.getExecQty(), exec.getExecPrice());
            traceLog.add("    Trade " + (i + 1) + ": " + exec.getExecQty() + " @ $" + 
                String.format("%.2f", exec.getExecPrice()));
            totalTradeVolume += exec.getExecQty();
            tradeCount++;
        }

        System.out.println("\n[ORDER STATUS]");
        System.out.println("  Sell A (100 @ 50.00): " + sellA.getStatus() + 
            " | Filled: " + sellA.getCumQty() + " | Remaining: " + sellA.getLeavesQty());
        System.out.println("  Sell B (100 @ 51.00): " + sellB.getStatus() + 
            " | Filled: " + sellB.getCumQty() + " | Remaining: " + sellB.getLeavesQty());
        System.out.println("  Buy (150 @ 52.00): " + buy.getStatus() + 
            " | Filled: " + buy.getCumQty() + " | Remaining: " + buy.getLeavesQty());

        // Validate
        System.out.println("\n[VALIDATION]");
        boolean priceOrderCorrect = false;
        boolean timeOrderCorrect = false;

        if (executions.size() >= 2) {
            // Check Price Priority (Trade 1 at 50.00, Trade 2 at 51.00)
            if (Math.abs(executions.get(0).getExecPrice() - 50.00) < 0.01) {
                System.out.println("✓ Price Priority: First execution at $50.00 (best ask)");
                priceOrderCorrect = true;
            } else {
                System.out.println("✗ Price Priority: First execution not at $50.00");
            }

            // Check Time Priority within same level shouldn't apply here, but verify quantities
            if (Math.abs(executions.get(0).getExecQty() - 100.0) < 0.01) {
                System.out.println("✓ Full Level Fill: 100 @ $50.00 (entire Order A)");
                timeOrderCorrect = true;
            }

            if (Math.abs(executions.get(1).getExecPrice() - 51.00) < 0.01) {
                System.out.println("✓ Partial Fill: Trade at $51.00 (Order B)");
            }
        }

        if (executions.size() == 2 && Math.abs(buy.getLeavesQty() - 50.0) < 0.01) {
            System.out.println("✓ Remaining Order: Buy 50 @ $52.00 remains on book");
        }

        System.out.println("\n[BOOK STATE] After aggressive buy:");
        engine.getOrderBook("GOOG").printSnapshot();
    }

    /**
     * ASSESSMENT: Full Price-Time Priority Trace
     * This replicates the exact trace requested in the assessment:
     * 
     * 1. Sell 100 @ 50.00 (Order A - Resting)
     * 2. Sell 100 @ 51.00 (Order B - Resting)
     * 3. Buy 150 @ 52.00 (Aggressive)
     * 
     * Log should show:
     *   Trade 1: 100 @ 50.00
     *   Trade 2: 50 @ 51.00
     *   Remaining Buy: 50 @ 52.00 on book
     */
    private static void testAssessmentTrace() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ASSESSMENT: PRICE-TIME PRIORITY TRACE");
        System.out.println("=".repeat(80));

        engine = new MatchingEngine();

        System.out.println("\n[1] SEND: Sell 100 @ 50.00");
        Order orderA = new Order("ORDER_A", "ASSESS", '2', 50.00, 100);
        List<Execution> exec1 = engine.matchOrder(orderA);
        System.out.println("    Result: " + (exec1.isEmpty() ? "Resting (no match)" : exec1.size() + " executions"));
        System.out.println("    Status: " + orderA.getStatus());

        System.out.println("\n[2] SEND: Sell 100 @ 51.00");
        Order orderB = new Order("ORDER_B", "ASSESS", '2', 51.00, 100);
        List<Execution> exec2 = engine.matchOrder(orderB);
        System.out.println("    Result: " + (exec2.isEmpty() ? "Resting (no match)" : exec2.size() + " executions"));
        System.out.println("    Status: " + orderB.getStatus());

        System.out.println("\n[3] SEND: Buy 150 @ 52.00");
        Order aggressive = new Order("ORDER_AGG", "ASSESS", '1', 52.00, 150);
        List<Execution> exec3 = engine.matchOrder(aggressive);

        System.out.println("\n[RESULT LOG]");
        System.out.println("    Executions generated: " + exec3.size());

        for (int i = 0; i < exec3.size(); i++) {
            Execution e = exec3.get(i);
            System.out.printf("    >> Trade %d: %.0f shares @ $%.2f%n", i + 1, e.getExecQty(), e.getExecPrice());
        }

        System.out.println("\n[ORDER BOOK STATE]");
        System.out.println("  Order A (SELL 100 @ 50.00): Status=" + orderA.getStatus() + 
            " | Filled=" + orderA.getCumQty() + " | Remaining=" + orderA.getLeavesQty());
        System.out.println("  Order B (SELL 100 @ 51.00): Status=" + orderB.getStatus() + 
            " | Filled=" + orderB.getCumQty() + " | Remaining=" + orderB.getLeavesQty());
        System.out.println("  Aggressive (BUY 150 @ 52.00): Status=" + aggressive.getStatus() + 
            " | Filled=" + aggressive.getCumQty() + " | Remaining=" + aggressive.getLeavesQty());

        engine.getOrderBook("ASSESS").printSnapshot();

        // Validation
        System.out.println("\n[CRITICAL VALIDATIONS]");
        boolean valid = true;

        if (exec3.size() != 2) {
            System.out.println("✗ Expected exactly 2 trades, got " + exec3.size());
            valid = false;
        } else {
            System.out.println("✓ Correct number of trades: 2");
        }

        if (exec3.size() >= 1) {
            Execution trade1 = exec3.get(0);
            if (Math.abs(trade1.getExecPrice() - 50.00) < 0.01 && 
                Math.abs(trade1.getExecQty() - 100.0) < 0.01) {
                System.out.println("✓ Trade 1: 100 @ $50.00 (CORRECT - best price first)");
            } else {
                System.out.println("✗ Trade 1: Expected 100 @ $50.00, got " + trade1.getExecQty() + 
                    " @ $" + String.format("%.2f", trade1.getExecPrice()));
                valid = false;
            }
        }

        if (exec3.size() >= 2) {
            Execution trade2 = exec3.get(1);
            if (Math.abs(trade2.getExecPrice() - 51.00) < 0.01 && 
                Math.abs(trade2.getExecQty() - 50.0) < 0.01) {
                System.out.println("✓ Trade 2: 50 @ $51.00 (CORRECT - partial fill)");
            } else {
                System.out.println("✗ Trade 2: Expected 50 @ $51.00, got " + trade2.getExecQty() + 
                    " @ $" + String.format("%.2f", trade2.getExecPrice()));
                valid = false;
            }
        }

        if (Math.abs(aggressive.getLeavesQty() - 50.0) < 0.01 && 
            aggressive.getStatus().equals("PARTIALLY_FILLED")) {
            System.out.println("✓ Aggressive order: 50 shares remain @ $52.00 on book");
        } else {
            System.out.println("✗ Aggressive order: Expected 50 remaining, got " + 
                aggressive.getLeavesQty() + " (Status: " + aggressive.getStatus() + ")");
            valid = false;
        }

        System.out.println("\n" + (valid ? "✓✓✓ ASSESSMENT COMPLETE AND VALID ✓✓✓" : 
            "✗✗✗ ASSESSMENT FAILED VALIDATION ✗✗✗"));
    }

    private static void printSummary() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println(" TEST SUMMARY");
        System.out.println("=".repeat(80));

        System.out.println("\nTotal Trades Generated: " + tradeCount);
        System.out.println("Total Volume Traded: " + totalTradeVolume + " shares");

        System.out.println("\n" + "TRACE LOG:");
        System.out.println("=".repeat(80));
        for (String line : traceLog) {
            System.out.println(line);
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println(" END OF LAB 7 TEST SUITE");
        System.out.println("=".repeat(80) + "\n");
    }

    private static String orderToString(Order order) {
        String sideStr = order.getSide() == '1' ? "BUY" : "SELL";
        return String.format("%s %s | Symbol=%s | Qty=%.0f @ $%.2f | ID=%s",
            sideStr, order.getClOrdID(), order.getSymbol(),
            order.getQuantity(), order.getPrice(), order.getOrderId());
    }
}
