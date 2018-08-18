package org.openpredict.exchange.core;

import com.google.common.primitives.Longs;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openpredict.exchange.beans.L2MarketData;
import org.openpredict.exchange.beans.MatcherEventType;
import org.openpredict.exchange.beans.MatcherTradeEvent;
import org.openpredict.exchange.beans.OrderAction;
import org.openpredict.exchange.beans.cmd.CommandResultCode;
import org.openpredict.exchange.beans.cmd.OrderCommand;
import org.openpredict.exchange.tests.util.L2MarketDataHelper;
import org.openpredict.exchange.tests.util.TestOrdersGenerator;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.openpredict.exchange.beans.OrderAction.ASK;
import static org.openpredict.exchange.beans.OrderAction.BID;
import static org.openpredict.exchange.beans.cmd.CommandResultCode.MATCHING_INVALID_ORDER_ID;
import static org.openpredict.exchange.beans.cmd.CommandResultCode.SUCCESS;

/**
 * TODO add tests where orders for same UID ignored during matching
 * TODO cancel/update other uid not allowed
 */
@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class OrderBookTest {

    private IOrderBook orderBook;

    private L2MarketDataHelper expectedState;


    private static final int UID_1 = 412;
    private static final int UID_2 = 413;

    //private QueuedEventSink<MatcherTradeEvent> tradesConsumer;

    @Before
    public void before() {
        //tradesConsumer = new QueuedEventSink<>(MatcherTradeEvent::new, 1024);

        orderBook = IOrderBook.newInstance();
        orderBook.validateInternalState();

        orderBook.processCommand(OrderCommand.limitOrder(1, UID_1, 1600, 100, ASK));
        orderBook.processCommand(OrderCommand.limitOrder(2, UID_1, 1599, 50, ASK));
        orderBook.processCommand(OrderCommand.limitOrder(3, UID_1, 1599, 25, ASK));
        orderBook.validateInternalState();

        orderBook.processCommand(OrderCommand.limitOrder(4, UID_1, 1593, 40, BID));
        orderBook.processCommand(OrderCommand.limitOrder(5, UID_1, 1590, 20, BID));
        orderBook.processCommand(OrderCommand.limitOrder(6, UID_1, 1590, 1, BID));
        orderBook.processCommand(OrderCommand.limitOrder(7, UID_1, 1200, 20, BID));
        orderBook.validateInternalState();

        expectedState = new L2MarketDataHelper(
                new L2MarketData(
                        new long[]{1599, 1600},
                        new long[]{75, 100},
                        new long[]{1593, 1590, 1200},
                        new long[]{40, 21, 20}
                )
        );
    }

    @After
    public void after() {

        orderBook.validateInternalState();
        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10000);

        // match all asks
        long askSum = Arrays.stream(snapshot.askVolumes).sum();
        orderBook.processCommand(OrderCommand.marketOrder(100000000000L, -1, askSum, BID));

//        log.debug("{}", dumpOrderBook(orderBook.getL2MarketDataSnapshot(100000)));

        orderBook.validateInternalState();

        // match all bids
        long bidSum = Arrays.stream(snapshot.bidVolumes).sum();
        orderBook.processCommand(OrderCommand.marketOrder(100000000001L, -2, bidSum, ASK));

//        log.debug("{}", dumpOrderBook(orderBook.getL2MarketDataSnapshot(100000)));

        assertThat(orderBook.getL2MarketDataSnapshot(10).askSize, is(0));
        assertThat(orderBook.getL2MarketDataSnapshot(10).bidSize, is(0));

        orderBook.validateInternalState();
    }


    // ------------------------ TESTS WITHOUT MATCHING -----------------------

    @Test
    public void shouldAddLimitOrders() {

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        log.debug("{}", dumpOrderBook(snapshot));


//        NavigableMap<Long, OrdersBucketSlow> askBuckets = getAskBuckets();
//        NavigableMap<Long, OrdersBucketSlow> bidBuckets = getBidBuckets();
//        assertThat(askBuckets.size(), is(2));
//        assertThat(askBuckets.get(1600L).getPrice(), is(1600L));
//        assertThat(askBuckets.get(1600L).entries.size(), is(1));
//        assertThat(askBuckets.get(1599L).entries.size(), is(2));
//        assertThat(bidBuckets.size(), is(3));
//        assertThat(bidBuckets.get(1590L).entries.size(), is(2));

        assertEquals(expectedState.build(), snapshot);

        //assertThat(extractEvents().size(), is(0));
    }


    @Test
    public void shouldRemoveOrder() {

        //log.debug("{}", dumpOrderBook(orderBook.getL2MarketDataSnapshot(10)));

        OrderCommand cmd = OrderCommand.cancel(5, UID_1);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        assertEquals(expectedState.setBidVolume(1, 1).build(), snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkReduce(events.get(0), 5L, BID, 20L, UID_1);

    }

    @Test
    public void shouldRemoveOrderAndEmptyBucket() {

        OrderCommand cmd = OrderCommand.cancel(2, UID_1);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkReduce(events.get(0), 2L, ASK, 50L, UID_1);


        cmd = OrderCommand.cancel(3, UID_1);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        log.debug("{}", dumpOrderBook(snapshot));

        assertEquals(expectedState.removeAsk(0).build(), snapshot);

        events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkReduce(events.get(0), 3L, ASK, 25L, UID_1);

    }

    @Test
    public void shouldReturnErrorWhenRemoveUnknownOrder() {

        OrderCommand cmd = OrderCommand.cancel(5291, UID_1);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(MATCHING_INVALID_ORDER_ID));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        log.debug("{}", dumpOrderBook(snapshot));

        // nothing has changed
        assertEquals(expectedState.build(), snapshot);
    }

    @Test
    public void shouldReturnErrorWhenUpdatingUnknownOrder() {

        OrderCommand cmd = OrderCommand.update(2433, UID_1, 300, 5);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(MATCHING_INVALID_ORDER_ID));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        //        log.debug("{}", dumpOrderBook(snapshot));

        assertEquals(expectedState.build(), snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(0));
    }

    @Test
    public void shouldReduceOrderSize() {

        OrderCommand cmd = OrderCommand.update(2, UID_1, 0, 5);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        // volume reduced
        L2MarketData expected = expectedState.setAskVolume(0, 30).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkReduce(events.get(0), 2L, ASK, 45L, UID_1);
    }

    @Test
    public void shouldMoveOrderExistingBucket() {
        OrderCommand cmd = OrderCommand.update(7, UID_1, 1590, 0);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        // moved
        L2MarketData expected = expectedState.setBidVolume(1, 41).removeBid(2).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(0));
    }

    @Test
    public void shouldMoveOrderNewBucket() {
        OrderCommand cmd = OrderCommand.update(7, UID_1, 1594, 0);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        // moved
        L2MarketData expected = expectedState.removeBid(2).insertBid(0, 1594, 20).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(0));
    }

    @Test
    public void shouldMoveAndReduceOrder() {
        OrderCommand cmd = OrderCommand.update(7, UID_1, 1590, 1);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));

        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        // volume reduced and moved
        L2MarketData expected = expectedState.setBidVolume(1, 22).removeBid(2).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkReduce(events.get(0), 7L, BID, 19L, UID_1);
    }


    // ------------------------ MATCHING TESTS -----------------------

    @Test
    public void shouldMatchMarketOrderPartialBBO() {

        // size=10
        OrderCommand cmd = OrderCommand.marketOrder(123, UID_2, 10, ASK);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best bid matched
        L2MarketData expected = expectedState.setBidVolume(0, 30).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkTrade(events.get(0), 123L, 4L, 1593, 10L);
    }


    @Test
    public void shouldMatchMarketOrderFullBBO() {

        // size=40
        OrderCommand cmd = OrderCommand.marketOrder(123, UID_2, 40, ASK);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best bid matched
        L2MarketData expected = expectedState.removeBid(0).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkTrade(events.get(0), 123L, 4L, 1593, 40L);
    }

    @Test
    public void shouldMatchMarketOrderWithTwoLimitOrdersPartial() {

        // size=41
        OrderCommand cmd = OrderCommand.marketOrder(123, UID_2, 41, ASK);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // bids matched
        L2MarketData expected = expectedState.removeBid(0).setBidVolume(0, 20).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(2));
        checkTrade(events.get(0), 123L, 4L, 1593, 40L);
        checkTrade(events.get(1), 123L, 5L, 1590, 1L);

        // check orders are removed from map
        assertNull(orderBook.getOrderById(4L));
        assertNotNull(orderBook.getOrderById(5L));
    }


    @Test
    public void shouldMatchMarketOrderFullLiquidity() {

        // size=175
        OrderCommand cmd = OrderCommand.marketOrder(123, UID_2, 175, BID);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // all asks matched
        L2MarketData expected = expectedState.removeAsk(0).removeAsk(0).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(3));
        checkTrade(events.get(0), 123L, 2L, 1599, 50L);
        checkTrade(events.get(1), 123L, 3L, 1599, 25L);
        checkTrade(events.get(2), 123L, 1L, 1600, 100L);

        // check orders are removed from map
        assertNull(orderBook.getOrderById(1L));
        assertNull(orderBook.getOrderById(2L));
        assertNull(orderBook.getOrderById(3L));
    }

    @Test
    public void shouldMatchMarketOrderWithRejection() {

        // size=200
        OrderCommand cmd = OrderCommand.marketOrder(123, UID_2, 200, BID);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // all asks matched
        L2MarketData expected = expectedState.removeAsk(0).removeAsk(0).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(4));

        // 3 trades generated and then rejection with size=25 left unmatched
        checkRejection(events.get(3), 123L, 25L);
    }

    // MARKETABLE LIMIT ORDERS

    @Test
    public void shouldFullyMatchMarketableLimitOrder() {

        // size=1
        OrderCommand cmd = OrderCommand.limitOrder(123, UID_2, 1599, 1, BID);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best ask partially matched
        L2MarketData expected = expectedState.setAskVolume(0, 74).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkTrade(events.get(0), 123L, 2L, 1599, 1L);
    }


    @Test
    public void shouldPartiallyMatchMarketableLimitOrderAndPlace() {

        // size=77
        OrderCommand cmd = OrderCommand.limitOrder(123, UID_2, 1599, 77, BID);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best asks fully matched, limit bid order placed
        L2MarketData expected = expectedState.removeAsk(0).insertBid(0, 1599, 2).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(2));

        checkTrade(events.get(0), 123L, 2L, 1599, 50L);
        checkTrade(events.get(1), 123L, 3L, 1599, 25L);
    }

    @Test
    public void shouldFullyMatchMarketableLimitOrder2Prices() {

        // size=77
        OrderCommand cmd = OrderCommand.limitOrder(123, UID_2, 1600, 77, BID);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best asks fully matched, limit bid order placed
        L2MarketData expected = expectedState.removeAsk(0).setAskVolume(0, 98).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(3));

        checkTrade(events.get(0), 123L, 2L, 1599, 50L);
        checkTrade(events.get(1), 123L, 3L, 1599, 25L);
        checkTrade(events.get(2), 123L, 1L, 1600, 2L);
    }


    @Test
    public void shouldFullyMatchMarketableLimitOrderWithAllLiquidity() {

        // size=1000
        OrderCommand cmd = OrderCommand.limitOrder(123, UID_2, 1630, 1000, BID);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best asks fully matched, limit bid order placed
        L2MarketData expected = expectedState.removeAsk(0).removeAsk(0).insertBid(0, 1630, 825).build();
        assertEquals(expected, snapshot);

        // trades only, rejection not generated for limit order
        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(3));

        checkTrade(events.get(0), 123L, 2L, 1599, 50L);
        checkTrade(events.get(1), 123L, 3L, 1599, 25L);
        checkTrade(events.get(2), 123L, 1L, 1600, 100L);

    }


    // Move limit order to marketable price

    @Test
    public void shouldMoveOrderFullyMatchAsMarketable() {

        // add new order and check it is there
        OrderCommand cmd = OrderCommand.limitOrder(83, UID_2, 1200, 20, BID);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(0));

        L2MarketData expected = expectedState.setBidVolume(2, 40).build();
        assertEquals(expected, orderBook.getL2MarketDataSnapshot(10));

        // downsize and move to marketable price area
        cmd = OrderCommand.update(83, UID_2, 1602, 18);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));

        orderBook.validateInternalState();

        // moved
        expected = expectedState.setBidVolume(2, 20).setAskVolume(0, 57).build();
        assertEquals(expected, orderBook.getL2MarketDataSnapshot(10));

        events = cmd.extractEvents();
        assertThat(events.size(), is(2));
        checkReduce(events.get(0), 83L, BID, 2L, UID_2);
        checkTrade(events.get(1), 83L, 2L, 1599, 18L);
    }


    @Test
    public void shouldMoveOrderFullyMatchAsMarketable2Prices() {

        OrderCommand cmd = OrderCommand.limitOrder(83, UID_2, 1594, 100, BID);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(0));

        // move to marketable zone
        cmd = OrderCommand.update(83, UID_2, 1600, 0);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        // moved
        L2MarketData expected = expectedState.removeAsk(0).setAskVolume(0, 75).build();
        assertEquals(expected, snapshot);

        events = cmd.extractEvents();
        assertThat(events.size(), is(3));
        checkTrade(events.get(0), 83L, 2L, 1599, 50L);
        checkTrade(events.get(1), 83L, 3L, 1599, 25L);
        checkTrade(events.get(2), 83L, 1L, 1600, 25L);

    }

    @Test
    public void shouldMoveOrderMatchesAllLiquidity() {

        OrderCommand cmd = OrderCommand.limitOrder(83, UID_2, 1594, 177, BID);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        // downsize and move to marketable zone
        cmd = OrderCommand.update(83, UID_2, 1601, 176);
        orderBook.processCommand(cmd);
        assertThat(cmd.resultCode, is(SUCCESS));
        orderBook.validateInternalState();

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        // moved
        L2MarketData expected = expectedState.removeAsk(0).removeAsk(0).insertBid(0, 1601, 1).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(4));
        checkReduce(events.get(0), 83L, BID, 1L, UID_2);
        checkTrade(events.get(1), 83L, 2L, 1599, 50L);
        checkTrade(events.get(2), 83L, 3L, 1599, 25L);
        checkTrade(events.get(3), 83L, 1L, 1600, 100L);
    }

    @Test
    public void multipleCommandsTest() {

        TestOrdersGenerator generator = new TestOrdersGenerator();

        int tranNum = 100000;

        orderBook = IOrderBook.newInstance();
        orderBook.validateInternalState();

        List<OrderCommand> testCommands = generator.generateCommands(tranNum, 200, Longs.asList(10, 11, 12, 13, 14, 15));

        testCommands.forEach(cmd -> {
            cmd.orderId += 100; // TODO set start id
            orderBook.processCommand(cmd);

            assertThat(cmd.resultCode, is(CommandResultCode.SUCCESS));
            orderBook.validateInternalState();
        });

    }

    // ------------------------------- UTILITY METHODS --------------------------

    public void checkTrade(MatcherTradeEvent event, long activeId, long matchedId, long price, long size) {

        assertThat(event.eventType, is(MatcherEventType.TRADE));

        assertThat(event.activeOrderId, is(activeId));
        assertThat(event.matchedOrderId, is(matchedId));
        assertThat(event.price, is(price));
        assertThat(event.size, is(size));
        // TODO add more checks for MatcherTradeEvent
    }

    public void checkRejection(MatcherTradeEvent event, long activeId, long size) {

        assertThat(event.eventType, is(MatcherEventType.REJECTION));

        assertThat(event.activeOrderId, is(activeId));
        assertThat(event.size, is(size));
        // TODO add more checks for MatcherTradeEvent
    }

    public void checkReduce(MatcherTradeEvent event, long orderId, OrderAction action, long reducedBy, long uid) {
        assertThat(event.eventType, is(MatcherEventType.REDUCE));

        assertThat(event.activeOrderId, is(orderId));
        assertThat(event.activeOrderAction, is(action));
        assertThat(event.size, is(reducedBy));

        assertThat(event.activeOrderUid, is(uid));
        // TODO add more checks for MatcherTradeEvent
    }

}