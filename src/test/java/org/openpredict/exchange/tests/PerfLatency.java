package org.openpredict.exchange.tests;

import lombok.extern.slf4j.Slf4j;
import net.openhft.affinity.AffinityLock;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SingleWriterRecorder;
import org.junit.Test;
import org.openpredict.exchange.beans.CoreSymbolSpecification;
import org.openpredict.exchange.beans.api.ApiCommand;
import org.openpredict.exchange.beans.cmd.OrderCommand;
import org.openpredict.exchange.tests.util.TestOrdersGenerator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.openpredict.exchange.tests.util.LatencyTools.createLatencyReportFast;

@Slf4j
public final class PerfLatency extends IntegrationTestBase {

    private static final boolean WRITE_HDR_HISTOGRAMS = false;

    @Test
    public void latencyTest() {
        latencyTestImpl(
                3_000_000,
                1_000,
                1_000,
                CURRENCIES_FUTURES,
                1,
                AllowedSymbolTypes.FUTURES_CONTRACT);
    }

    @Test
    public void latencyTestTripleMillion() {
        latencyTestImpl(
                8_000_000,
                1_075_000,
                1_000_000,
                CURRENCIES_FUTURES,
                1,
                AllowedSymbolTypes.FUTURES_CONTRACT);
    }

    @Test
    public void latencyMultiSymbol() {
        latencyTestImpl(
                10_000_000,
                50_000,
                100_000,
                ALL_CURRENCIES,
                23,
                AllowedSymbolTypes.BOTH);
    }


    private void latencyTestImpl(final int totalTransactionsNumber,
                                 final int targetOrderBookOrders,
                                 final int numUsers,
                                 final Set<Integer> currenciesAllowed,
                                 final int numSymbols,
                                 final AllowedSymbolTypes allowedSymbolTypes) {

//        int targetTps = 1000000; // transactions per second
        final int targetTps = 100_000; // transactions per second
        final int targetTpsEnd = 8_000_000;
        final int targetTpsStep = 25_000;

        final int warmupTps = 1_000_000;
        final int warmupCycles = 20;
//        int targetTps = 4_000_000; // transactions per second

        try (AffinityLock cpuLock = AffinityLock.acquireCore()) {

            final List<CoreSymbolSpecification> coreSymbolSpecifications = generateAndAddSymbols(numSymbols, currenciesAllowed, allowedSymbolTypes);

            final Set<Integer> symbols = coreSymbolSpecifications.stream().map(spec -> spec.symbolId).collect(Collectors.toSet());
            final Map<Integer, TestOrdersGenerator.GenResult> genResults = TestOrdersGenerator.generateMultipleSymbols(
                    totalTransactionsNumber,
                    targetOrderBookOrders,
                    numUsers,
                    symbols);

            final List<OrderCommand> commands = TestOrdersGenerator.mergeCommands(genResults.values());
            final List<ApiCommand> apiCommands = TestOrdersGenerator.convertToApiCommand(commands);

            SingleWriterRecorder hdrRecorder = new SingleWriterRecorder(10_000_000_000L, 3);

            // TODO - first run should validate the output (orders are accepted and processed properly)

            IntConsumer testIteration = tps -> {
                try {

                    initSymbol();
                    coreSymbolSpecifications.forEach(super::addSymbol);
                    usersInit(numUsers, currenciesAllowed);

                    hdrRecorder.reset();

                    final CountDownLatch latch = new CountDownLatch(apiCommands.size());
                    consumer = cmd -> {
                        hdrRecorder.recordValue((System.nanoTime() - cmd.timestamp));
                        latch.countDown();
                        //receiveCounter.lazySet(cmd.timestamp);
                    };

                    final int nanosPerCmd = (1_000_000_000 / tps);
                    final long startTimeMs = System.currentTimeMillis();

                    long plannedTimestamp = System.nanoTime();

                    for (ApiCommand cmd : apiCommands) {
                        while (System.nanoTime() < plannedTimestamp) {
                            // spin while too early for sending next message
                        }
                        cmd.timestamp = plannedTimestamp;
                        api.submitCommand(cmd);
                        plannedTimestamp += nanosPerCmd;
                    }

                    latch.await();
                    final long processingTimeMs = System.currentTimeMillis() - startTimeMs;
                    float perfMt = (float) apiCommands.size() / (float) processingTimeMs / 1000.0f;
                    String tag = String.format("%.3f MT/s", perfMt);
                    Histogram histogram = hdrRecorder.getIntervalHistogram();
                    log.info("{} {}", tag, createLatencyReportFast(histogram));

                    // compare orderBook final state just to make sure all commands executed same way
                    // TODO compare events, balances, portfolios
                    symbols.forEach(symbol -> assertEquals(genResults.get(symbol).getFinalOrderBookSnapshot(), requestCurrentOrderBook(symbol)));

                    if (WRITE_HDR_HISTOGRAMS) {
                        PrintStream printStream = new PrintStream(new File(System.currentTimeMillis() + "-" + perfMt + ".perc"));
                        //log.info("HDR 50%:{}", hdr.getValueAtPercentile(50));
                        histogram.outputPercentileDistribution(printStream, 1000.0);
                    }

                    resetExchangeCore();

                    System.gc();
                    Thread.sleep(300);

                } catch (InterruptedException | FileNotFoundException ex) {
                    ex.printStackTrace();
                }
            };

            log.debug("Warming up {} cycles...", warmupCycles);
            IntStream.range(0, warmupCycles)
                    .forEach(i -> testIteration.accept(warmupTps));
            log.debug("Warmup done, starting tests");

            IntStream.range(0, 10000)
                    .map(i -> targetTps + targetTpsStep * i)
                    .filter(tps -> tps <= targetTpsEnd)
                    .forEach(testIteration);
        }
    }
}