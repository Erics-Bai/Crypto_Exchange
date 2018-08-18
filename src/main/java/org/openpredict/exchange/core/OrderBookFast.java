package org.openpredict.exchange.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.openpredict.exchange.beans.L2MarketData;
import org.openpredict.exchange.beans.Order;
import org.openpredict.exchange.beans.OrderAction;
import org.openpredict.exchange.beans.OrderType;
import org.openpredict.exchange.beans.cmd.OrderCommand;

import java.util.*;

import static org.openpredict.exchange.beans.OrderAction.ASK;
import static org.openpredict.exchange.beans.OrderAction.BID;

@Slf4j
@RequiredArgsConstructor
public class OrderBookFast extends OrderBookBase {

    public static final int FAST_WIDTH = 65535;

    private BitSet hotAskBitSet = new BitSet(FAST_WIDTH);
    private BitSet hotBidBitSet = new BitSet(FAST_WIDTH);
    private LongObjectHashMap<IOrdersBucket> hotAskBuckets = new LongObjectHashMap<>();
    private LongObjectHashMap<IOrdersBucket> hotBidBuckets = new LongObjectHashMap<>();
    private long minAskPrice = Long.MAX_VALUE;
    private long maxBidPrice = 0;

    private long basePrice = -1; // TODO AUTO-DETECT

    private NavigableMap<Long, IOrdersBucket> farAskBuckets = new TreeMap<>();
    private NavigableMap<Long, IOrdersBucket> farBidBuckets = new TreeMap<>(Collections.reverseOrder());


    //    private LongObjectHashMap<Order> idMap = new LongObjectHashMap<>();
    private LongObjectHashMap<IOrdersBucket> idMapToBucket = new LongObjectHashMap<>();

    private final ArrayDeque<Order> ordersPool = new ArrayDeque<>(65536);
    private final ArrayDeque<IOrdersBucket> bucketsPool = new ArrayDeque<>(65536);

    private int priceToIndex(long price) {
        long idx = price - basePrice;
        if (idx < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        } else if (idx > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) idx;
    }

    private long indexToPrice(int idx) {
        return idx + basePrice;
    }

    /**
     * Process new MARKET order
     * Such order matched to any existing LIMIT orders
     * Of there is not enough volume in order book - reject as partially filled
     *
     * @param order - market order to match
     */
    protected void matchMarketOrder(OrderCommand order) {
        long filledSize = tryMatchInstantly(order, 0);

        // rare case - partially filled due no liquidity - should report PARTIAL order execution
        if (filledSize < order.size) {
            sendRejectEvent(order, filledSize);
        }
    }


    /**
     * Place new LIMIT order
     * If order is marketable (there are matching limit orders) - match it first with existing liquidity
     *
     * @param cmd - limit order to place
     */
    @Override
    protected void placeNewLimitOrder(OrderCommand cmd) {

        long orderId = cmd.orderId;
        if (idMapToBucket.containsKey(orderId)) {
            throw new IllegalArgumentException("duplicate orderId: " + orderId);
        }

        if (basePrice == -1) {
            // TODO fix
            basePrice = cmd.price - (FAST_WIDTH >> 1);
            basePrice = Math.max(0, basePrice);
            //basePrice = 0;
        }

        // check if order is marketable there are matching orders
        long filled = tryMatchInstantly(cmd, 0);
        if (filled == cmd.size) {
            // fully matched as marketable before actually place - can just return
            return;
        }

        OrderAction action = cmd.action;
        long price = cmd.price;

        // normally placing regular limit order

        Order orderRecord = ordersPool.pollLast();
        if (orderRecord == null) {
            orderRecord = new Order();
        } else {
            // log.debug(" <<< from pool pool ex:{}->{} {}", orderRecord.orderId, orderId, orderRecord);
        }

        orderRecord.command = cmd.command;
        orderRecord.orderId = orderId;
        orderRecord.symbol = cmd.symbol;
        orderRecord.price = price;
        orderRecord.size = cmd.size;
        orderRecord.action = action;
        orderRecord.orderType = cmd.orderType;
        orderRecord.uid = cmd.uid;
        orderRecord.timestamp = cmd.timestamp;
        orderRecord.filled = filled;

//        log.debug(" New object: {}", orderRecord);

        IOrdersBucket bucket = action == ASK ? getOrCreateNewBucketAck(price) : getOrCreateNewBucketBid(price);
        bucket.add(orderRecord);

        idMapToBucket.put(orderId, bucket);

    }

    private IOrdersBucket getOrCreateNewBucketAck(long price) {
        int idx = priceToIndex(price);
        if (idx < 0) {
            // TODO rebuild?
            throwPriceOutOfFastRangeException(price);
        }
        boolean far = (idx >= FAST_WIDTH);
        IOrdersBucket ordersBucket = far ? farAskBuckets.get(price) : hotAskBuckets.get(price);

        if (ordersBucket != null) {
            // bucket exists
            return ordersBucket;
        }

        ordersBucket = bucketsPool.pollLast();
        if (ordersBucket == null) {
            ordersBucket = IOrdersBucket.newInstance();
        }

        ordersBucket.setPrice(price);
        minAskPrice = Math.min(minAskPrice, price);

        if (far) {
            farAskBuckets.put(price, ordersBucket);
        } else {
            hotAskBuckets.put(price, ordersBucket);
            hotAskBitSet.set(idx);
        }

        return ordersBucket;
    }

    private IOrdersBucket getOrCreateNewBucketBid(long price) {
        int idx = priceToIndex(price);
        if (price >= basePrice + FAST_WIDTH) {
            // TODO rebuild?
            throwPriceOutOfFastRangeException(price);
        }
        boolean far = idx < 0;

        IOrdersBucket ordersBucket = far ? farBidBuckets.get(price) : hotBidBuckets.get(price);
        if (ordersBucket != null) {
            // bucket exists
            return ordersBucket;
        }

        ordersBucket = bucketsPool.pollLast();
        if (ordersBucket == null) {
            ordersBucket = IOrdersBucket.newInstance();
        }

        ordersBucket.setPrice(price);
        maxBidPrice = Math.max(maxBidPrice, price);

        if (far) {
            farBidBuckets.put(price, ordersBucket);
        } else {
            hotBidBuckets.put(price, ordersBucket);
            hotBidBitSet.set(idx);
        }
        return ordersBucket;
    }

    private void throwPriceOutOfFastRangeException(long price) {
        throw new IllegalArgumentException(String.format("Price %d out of bounds [%d,%d)", price, basePrice, basePrice + FAST_WIDTH));
    }

    /**
     * Match the order instantly to specified sorted buckets map
     * Fully matching orders are removed from orderId index
     * Should any trades occur - they sent to tradesConsumer
     *
     * @param order - LIMIT or MARKET order to match
     * @return matched size (0 if nothing is matching to the order)
     */
    private long tryMatchInstantly(OrderCommand order, long filled) {
        OrderAction action = order.action;

//      log.info("-------- matchInstantly: {}", order);
//      log.info("filled {} to match {}", filled, order.size);

        long nextPrice;
        long limitPrice;
        if (action == BID) {
            if (minAskPrice == Long.MAX_VALUE) {
                // no orders to match
                return filled;
            }
            nextPrice = minAskPrice;
            limitPrice = (order.orderType == OrderType.LIMIT) ? order.price : Long.MAX_VALUE;
        } else {
            if (maxBidPrice == 0) {
                // no orders to match
                return filled;
            }
            nextPrice = maxBidPrice;
            limitPrice = (order.orderType == OrderType.LIMIT) ? order.price : 0;
        }

        long orderSize = order.size;

        while (filled < orderSize) {

            // search for next available bucket
            IOrdersBucket bucket = (action == BID) ? nextAvailableBucketAsk(nextPrice, limitPrice) : nextAvailableBucketBid(nextPrice, limitPrice);
            if (bucket == null) {
                break;
            }

            final long tradePrice = bucket.getPrice();
            // next iteration price
            nextPrice = (action == BID) ? tradePrice + 1 : tradePrice - 1;

            TradeEventCallback tradeEventCallback = (mOrder, v, fm, fma) -> {
                sendTradeEvent(order, mOrder, fm, fma, tradePrice, v);
                if (fm) {
                    // forget if fully matched
                    idMapToBucket.remove(mOrder.orderId);
                    // saving free object back to pool
                    ordersPool.addLast(mOrder);
                }
            };

            // matching orders within bucket
            long sizeLeft = orderSize - filled;
            filled += bucket.match(sizeLeft, order.uid, tradeEventCallback);

            // remove bucket if its empty
            if (bucket.getTotalVolume() == 0) {
                if (action == BID) {
                    removeAskBucket(tradePrice);
                } else {
                    removeBidBucket(tradePrice);
                }
            }
        }
        return filled;
    }

    /**
     * Searches for next available bucket for matching starting from currentPrice inclusive and till lastPrice inclusive.
     *
     * @param currentPrice - price to start with
     * @param lastPrice    - limit price, can also be 0 or LONG_MAX for market orders.
     * @return bucket or null if not found
     */
    private IOrdersBucket nextAvailableBucketAsk(long currentPrice, long lastPrice) {
        int idx = priceToIndex(currentPrice);
        // normally searching within hot buckets
        if (idx < FAST_WIDTH) {
            int nextIdx = hotAskBitSet.nextSetBit(idx);
            // log.debug("A next {} for currentPrice={} lastPrice={}", next, currentPrice, lastPrice);
            if (nextIdx >= 0) {
                // found a bucket, but if limit is reached - no need to check far orders, just return null
                long nextPrice = nextIdx + basePrice;
                return nextPrice <= lastPrice ? hotAskBuckets.get(nextPrice) : null;
            }
        }

        // TODO independent searching can be slower comparing to processing a subtree (NLogN vs N) for superorders
        // nothing yet found and limit also not reached yet, therefore trying to search far buckets
        Map.Entry<Long, IOrdersBucket> entry = farAskBuckets.ceilingEntry(currentPrice);
        return (entry != null && entry.getKey() <= lastPrice) ? entry.getValue() : null;
    }

    /**
     * Searches for next available bucket for matching starting from currentPrice inclusive and till lastPrice inclusive.
     *
     * @param currentPrice - price to start with
     * @param lastPrice    - limit price, can also be 0 or INT_MAX for market orders.
     * @return bucket or null if not found
     */
    private IOrdersBucket nextAvailableBucketBid(long currentPrice, long lastPrice) {
        int idx = priceToIndex(currentPrice);
        // normally searching within hot buckets
        if (idx >= 0) {
            int nextIdx = hotBidBitSet.previousSetBit(idx);
            // log.debug("B next {} for currentPrice={} lastPrice={}", next, currentPrice, lastPrice);
            if (nextIdx >= 0) {
                // found a bucket, but if limit is reached - no need to check far orders, just return null
                long nextPrice = nextIdx + basePrice;
                return (nextPrice >= lastPrice) ? hotBidBuckets.get(nextPrice) : null;
            }
        }

        // TODO independent searching can be slower comparing to processing a subtree (NLogN vs N) for superorders
        // nothing yet found and limit also not reached yet, therefore trying to search far buckets
        // note: bid far buckets tree order is reversed, so searching ceiling key like for asks
        Map.Entry<Long, IOrdersBucket> entry = farBidBuckets.ceilingEntry(currentPrice);
        return (entry != null && entry.getKey() >= lastPrice) ? entry.getValue() : null;
    }


    /**
     * Cancel an order.
     * <p>
     * orderId - order to cancel
     *
     * @return true if order removed, false if not found (can be removed/matched earlier)
     */
    @Override
    public boolean cancelOrder(OrderCommand cmd) {

        // can not remove because uid is not verified yet
        IOrdersBucket ordersBucket = idMapToBucket.get(cmd.orderId);
        if (ordersBucket == null) {
            // order already matched and removed from order book previously
            return false;
        }

        // remove order and whole bucket if bucket is empty
        Order removedOrder = ordersBucket.remove(cmd.orderId, cmd.uid);
        if (removedOrder == null) {
            // uid is different
            return false;
        }

        // remove from map
        idMapToBucket.remove(cmd.orderId);

        // remove bucket if cancelled order was the last one in the bucket
        if (ordersBucket.getTotalVolume() == 0) {
            if (removedOrder.action == ASK) {
                removeAskBucket(ordersBucket.getPrice());
            } else {
                removeBidBucket(ordersBucket.getPrice());
            }
        }

        // send reduce event
        long reducedBy = removedOrder.size - removedOrder.filled;
        sendReduceEvent(removedOrder, reducedBy);

        // saving free object back to the pool
        ordersPool.addLast(removedOrder);

        return true;
    }


    /**
     * Reduce volume or/and move an order
     * <p>
     * Normally requires 4 hash table lookup operations.
     * 1. Find bucket by orderId
     * (optional reduce, validate price)
     * 2. Find in remove order in the bucket (remove from internal queue and hash table)
     * (optional remove bucket)
     * (set new price and try match instantly)
     * 3. Find bucket for new price
     * 4. Insert order in the bucket (internal hash table and queue)
     * <p>
     * orderId  - order id
     * newPrice - new price (0 - don't move the order)
     * newSize  - new size (0 - don't reduce size of the order)
     *
     * @return - false if order not found (can be matched or removed), true otherwise
     */
    @Override
    public boolean updateOrder(OrderCommand cmd) {

        long orderId = cmd.orderId;
        long newSize = cmd.size;
        long newPrice = cmd.price;

        IOrdersBucket bucket = idMapToBucket.get(orderId);
        if (bucket == null) {
            return false;
        }


        // if change volume operation - use bucket implementation
        // NOTE: not very efficient if moving and reducing volume
        if (newSize > 0) {
            if (!bucket.tryReduceSize(cmd, super::sendReduceEvent)) {
                return false;
            }
        }

        // return if there is no move operation (downsize only)
        if (newPrice <= 0 || newPrice == bucket.getPrice()) {
            return true;
        }

        // take order out of the original bucket
        Order order = bucket.remove(orderId, cmd.uid);
        if (order == null) {
            return false;
        }

        // remove bucket if moved order was the last one in the bucket
        if (bucket.getTotalVolume() == 0) {
            if (order.action == ASK) {
                removeAskBucket(bucket.getPrice());
            } else {
                removeBidBucket(bucket.getPrice());
            }
        }

        order.price = newPrice;
        //if ((action == BID && newPrice >= minAskPrice) || (action == ASK && newPrice <= maxBidPrice)) {
        // try match with new price
        long filled = tryMatchInstantly(order, order.filled);
        if (filled == order.size) {
            // order was fully matched (100% marketable) - removing from order book
            idMapToBucket.remove(orderId);
            // saving free object back to pool
            ordersPool.addLast(order);
            return true;
        }
        order.filled = filled;

        // if not filled completely - put it into corresponding bucket
        bucket = (order.action == ASK) ? getOrCreateNewBucketAck(newPrice) : getOrCreateNewBucketBid(newPrice);
        bucket.add(order);
        idMapToBucket.put(orderId, bucket);
        return true;
    }


    //    private void removeBucket(long price, OrderAction action) {
//
//        if (price < basePrice || price >= basePrice + FAST_WIDTH) {
//            throwPriceOutOfFastRangeException(price);
//        }
//
//
//        if (action == ASK) {
//
//            removeAskBucket(price);
//
//
//        } else {
//            hotBidBitSet.clear(idx);
//            bucketsPool.addLast(hotBidBuckets.remove(idx));
//            if (hotMaxBidIdx == idx) {
//                hotMaxBidIdx = hotBidBitSet.previousSetBit(hotMaxBidIdx);
//                if (hotMaxBidIdx == -1) {
//                    hotMaxBidIdx = 0;
//                }
//            }
//        }
//    }
//
    private void removeAskBucket(long price) {
        int idx = priceToIndex(price);

        if (idx < FAST_WIDTH) {
            // in hot area
            hotAskBitSet.clear(idx);
            bucketsPool.addLast(hotAskBuckets.remove(price));
        } else {
            // in far area
            bucketsPool.addLast(farAskBuckets.remove(price));
        }

        if (minAskPrice != price) {
            // do need to update minAskPrice
            return;
        }

        if (idx < FAST_WIDTH) {
            int nextIdx = hotAskBitSet.nextSetBit(idx);
            if (nextIdx >= 0) {
                // found new minAskPrice in hot bitset
                minAskPrice = nextIdx + basePrice;
                return;
            }
        }

        Long p = farAskBuckets.higherKey(price);
        minAskPrice = (p != null) ? p : Long.MAX_VALUE;
    }

    private void removeBidBucket(long price) {
        int idx = priceToIndex(price);

        if (idx >= 0) {
            // in hot area
            hotBidBitSet.clear(idx);
            bucketsPool.addLast(hotBidBuckets.remove(price));
        } else {
            // in far area
            bucketsPool.addLast(farBidBuckets.remove(price));
        }

        if (maxBidPrice != price) {
            // do not need to update maxBidPrice
            return;
        }

        if (idx >= 0) {
            int nextIdx = hotBidBitSet.previousSetBit(idx);
            if (nextIdx >= 0) {
                // found new maxBidPrice in hot bitset
                maxBidPrice = nextIdx + basePrice;
                return;
            }
        }

        // higher because of opposite sort order
        Long p = farBidBuckets.higherKey(price);
        maxBidPrice = (p != null) ? p : 0;
    }


    /**
     * Get order from internal map
     * Testing only
     *
     * @param orderId -
     * @return - order
     */
    @Override
    public Order getOrderById(long orderId) {
        IOrdersBucket bucket = idMapToBucket.get(orderId);
        return (bucket != null) ? bucket.findOrder(orderId) : null;
    }

    @Override
    protected void fillAsks(final int size, L2MarketData data) {
        if (minAskPrice == Long.MAX_VALUE || size == 0) {
            data.askSize = 0;
            return;
        }

        int next = priceToIndex(minAskPrice);
        int i = 0;
        while ((next = hotAskBitSet.nextSetBit(next)) >= 0) {
            IOrdersBucket bucket = hotAskBuckets.get(indexToPrice(next));
            data.askPrices[i] = bucket.getPrice();
            data.askVolumes[i] = bucket.getTotalVolume();
            if (++i == size) {
                data.askSize = size;
                return;
            }
            next++;
        }

        // extracting buckets from far trees
        for (IOrdersBucket bucket : farAskBuckets.values()) {
            data.askPrices[i] = bucket.getPrice();
            data.askVolumes[i] = bucket.getTotalVolume();
            if (++i == size) {
                data.askSize = size;
                return;
            }
        }

        // not filled completely
        data.askSize = i;
    }

    @Override
    protected void fillBids(final int size, L2MarketData data) {

        if (maxBidPrice == 0 || size == 0) {
            data.bidSize = 0;
            return;
        }


        int next = priceToIndex(maxBidPrice);
        int i = 0;
        while ((next = hotBidBitSet.previousSetBit(next)) >= 0) {
            IOrdersBucket bucket = hotBidBuckets.get(indexToPrice(next));
            data.bidPrices[i] = bucket.getPrice();
            data.bidVolumes[i] = bucket.getTotalVolume();
            if (++i == size) {
                data.bidSize = size;
                return;
            }
            next--;
        }

        // extracting buckets from far trees
        // note: farBidBuckets is in reversed order
        for (IOrdersBucket bucket : farBidBuckets.values()) {
            data.bidPrices[i] = bucket.getPrice();
            data.bidVolumes[i] = bucket.getTotalVolume();
            if (++i == size) {
                data.bidSize = size;
                return;
            }
        }

        // not filled completely
        data.bidSize = i;
    }

    @Override
    protected int getTotalAskBuckets() {
        return hotAskBuckets.size() + farAskBuckets.size();
    }

    @Override
    protected int getTotalBidBuckets() {
        return hotBidBuckets.size() + farBidBuckets.size();
    }

    @Override
    public void validateInternalState() {
        // validateInternalState each bucket
        hotAskBuckets.stream().forEach(IOrdersBucket::validate);
        hotBidBuckets.stream().forEach(IOrdersBucket::validate);
        // TODO validateInternalState bitset, BBO, orderid maps
    }

    // for testing only
    @Override
    public void clear() {
        hotAskBuckets.clear();
        hotBidBuckets.clear();
        hotAskBitSet.clear();
        hotBidBitSet.clear();
        farAskBuckets.clear();
        farBidBuckets.clear();
        minAskPrice = Long.MAX_VALUE;
        maxBidPrice = 0;
        idMapToBucket.clear();
//        ordersPool.clear(); // ?
    }


    // for testing only
    @Override
    public int getOrdersNum() {

        //validateInternalState();

        // TODO add trees
        int askOrders = hotAskBuckets.values().stream().mapToInt(IOrdersBucket::getNumOrders).sum();
        int bidOrders = hotBidBuckets.values().stream().mapToInt(IOrdersBucket::getNumOrders).sum();

//        log.debug("idMap:{} askOrders:{} bidOrders:{}", idMap.size(), askOrders, bidOrders);
        int knownOrders = idMapToBucket.size();

        assert knownOrders == askOrders + bidOrders : "inconsistent known orders";

        return idMapToBucket.size();
    }

}
