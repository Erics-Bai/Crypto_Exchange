package org.openpredict.exchange.tests;

import lombok.extern.slf4j.Slf4j;
import net.openhft.affinity.AffinityLock;
import org.junit.Test;
import org.openpredict.exchange.beans.CoreSymbolSpecification;
import org.openpredict.exchange.tests.util.TestOrdersGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

@Slf4j
public final class PerfThroughput extends IntegrationTestBase {

    // TODO shutdown disruptor if test fails

    /**
     * This is throughput test for simplified conditions
     * - one symbol
     * - 1K active users (~2K currency accounts)
     * - 1K pending limit-orders (in one order book)
     * 6-threads CPU can run this test
     */
    @Test
    public void throughputTest() throws Exception {
        initExchange(2 * 1024, 1, 1, 1536);
        throughputTestImpl(
                3_000_000,
                1000,
                1000,
                50,
                CURRENCIES_FUTURES,
                1,
                AllowedSymbolTypes.FUTURES_CONTRACT);
    }

    /**
     * This is high load throughput test for verifying "triple million" capability:
     * - 1M active users (~5M currency accounts)
     * - 1M pending limit-orders (in 1K order books)
     * - at least 1M messages per second throughput
     * 12-threads CPU is required for running this test in 4+4 configuration.
     */
    @Test
    public void throughputMultiSymbol() throws Exception {
        initExchange(64 * 1024, 4, 4, 2048);
        throughputTestImpl(
                5_000_000,
                1_000_000,
                1_000_000,
                25,
                ALL_CURRENCIES,
                1_000,
                AllowedSymbolTypes.BOTH);
    }

    private void throughputTestImpl(final int totalTransactionsNumber,
                                    final int targetOrderBookOrdersTotal,
                                    final int numUsers,
                                    final int iterations,
                                    final Set<Integer> currenciesAllowed,
                                    final int numSymbols,
                                    final AllowedSymbolTypes allowedSymbolTypes) throws InterruptedException {

        try (AffinityLock cpuLock = AffinityLock.acquireCore()) {

            final List<CoreSymbolSpecification> coreSymbolSpecifications = generateAndAddSymbols(numSymbols, currenciesAllowed, allowedSymbolTypes);

            TestOrdersGenerator.MultiSymbolGenResult genResult = TestOrdersGenerator.generateMultipleSymbols(coreSymbolSpecifications,
                    totalTransactionsNumber,
                    numUsers,
                    targetOrderBookOrdersTotal);

            List<Float> perfResults = new ArrayList<>();
            for (int j = 0; j < iterations; j++) {

                initBasicSymbols();
                coreSymbolSpecifications.forEach(super::addSymbol);
                usersInit(numUsers, currenciesAllowed);

                final CountDownLatch latchFill = new CountDownLatch(genResult.getApiCommandsFill().size());
                consumer = cmd -> latchFill.countDown();
                genResult.getApiCommandsFill().forEach(api::submitCommand);
                latchFill.await();


                final CountDownLatch latchBenchmark = new CountDownLatch(genResult.getApiCommandsBenchmark().size());
                consumer = cmd -> latchBenchmark.countDown();
                long t = System.currentTimeMillis();
                genResult.getApiCommandsBenchmark().forEach(api::submitCommand);
                latchBenchmark.await();
                t = System.currentTimeMillis() - t;
                float perfMt = (float) genResult.getApiCommandsBenchmark().size() / (float) t / 1000.0f;
                log.info("{}. {} MT/s", j, String.format("%.3f", perfMt));
                perfResults.add(perfMt);

                // compare orderBook final state just to make sure all commands executed same way
                // TODO compare events, balances, portfolios
                coreSymbolSpecifications.forEach(
                        symbol -> assertEquals(genResult.getGenResults().get(symbol.symbolId).getFinalOrderBookSnapshot(), requestCurrentOrderBook(symbol.symbolId)));

                resetExchangeCore();

                System.gc();
                Thread.sleep(300);
            }

            float avg = (float) perfResults.stream().mapToDouble(x -> x).average().orElse(0);
            log.info("Average: {} MT/s", avg);
        }
    }
}