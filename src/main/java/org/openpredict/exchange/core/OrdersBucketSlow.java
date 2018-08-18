package org.openpredict.exchange.core;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.openpredict.exchange.beans.Order;
import org.openpredict.exchange.beans.cmd.OrderCommand;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@NoArgsConstructor
@Slf4j
@ToString
public class OrdersBucketSlow implements IOrdersBucket {

    @Getter
    @Setter
    private long price;

    //    private Long2ObjectMap<Order> entries = new Long2ObjectLinkedOpenHashMap<>();
    private LinkedHashMap<Long, Order> entries = new LinkedHashMap<>();

    @Getter
    private long totalVolume = 0;

    /**
     * Place order into end of bucket
     *
     * @param order
     */

    @Override
    public void add(Order order) {
        entries.put(order.orderId, order);
        totalVolume += order.size - order.filled;
    }

    /**
     * Remove order
     *
     * @param orderId
     * @return
     */
    @Override
    public Order remove(long orderId, long uid) {
        Order order = entries.get(orderId);
//        log.debug("removing order: {}", order);
        if (order == null || order.uid != uid) {
            return null;
        }

        entries.remove(orderId);

        totalVolume -= order.size - order.filled;
        return order;
    }

    /**
     * Collect a list of matching orders starting from eldest records
     * Completely matching orders will be removed, partially matched order kept in the bucked.
     *
     * @param volumeToCollect
     * @return
     */
    @Override
    public long match(long volumeToCollect, long uid, TradeEventCallback lambda) {

//        log.debug("---- match: {}", volumeToCollect);

        Iterator<Map.Entry<Long, Order>> iterator = entries.entrySet().iterator();

        long totalMatchingVolume = 0;

        // iterate through all orders
        while (iterator.hasNext() && volumeToCollect > 0) {
            Map.Entry<Long, Order> next = iterator.next();
            Order order = next.getValue();

            if (order.uid == uid) {
                // continue uid
                continue;
            }

            // calculate exact volume can fill for this order
//            log.debug("volumeToCollect={} order: s{} f{}", volumeToCollect, order.size, order.filled);
            long v = Math.min(volumeToCollect, order.size - order.filled);
            totalMatchingVolume += v;
//            log.debug("totalMatchingVolume={} v={}", totalMatchingVolume, v);

            order.filled += v;
            volumeToCollect -= v;
            totalVolume -= v;

            // remove from order book filled orders
            boolean fullMatch = order.size == order.filled;

            lambda.submit(order, v, fullMatch, volumeToCollect == 0);

            if (fullMatch) {
                iterator.remove();
            }
        }

        return totalMatchingVolume;
    }

    /**
     * Reduce order volume if possible
     * <p>
     * orderId
     * newSize
     *
     * @return
     */
    @Override
    public boolean tryReduceSize(OrderCommand cmd, ReduceEventCallback callback) {
        Order order = entries.get(cmd.orderId);
        if (order == null || order.uid != cmd.uid) {
            return false;
        }

        long reduceBy = order.size - order.filled - cmd.size;
        if (reduceBy > 0) {
            order.size -= reduceBy;
            totalVolume -= reduceBy;
            callback.submit(order, reduceBy);
        }
        
        return true;
    }


    @Override
    public int getNumOrders() {
        return entries.size();
    }

    @Override
    public void validate() {

    }

    @Override
    public Order findOrder(long orderId) {
        return entries.get(orderId);
    }
}
