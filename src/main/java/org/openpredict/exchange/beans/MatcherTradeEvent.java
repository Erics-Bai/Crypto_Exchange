package org.openpredict.exchange.beans;


import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.builder.EqualsBuilder;

import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@ToString
public class MatcherTradeEvent {

    public MatcherEventType eventType; // TRADE, REDUCE or REJECTION (rare)

    public int symbol;

    // taker (for TRADE)
    public long activeOrderId;
    public long activeOrderUid;
    public boolean activeOrderCompleted; // false, except when activeOrder is completely filled
    public OrderAction activeOrderAction; // assume matched order has opposite action
//    public long activeOrderSeq;

    // maker (for TRADE)
    public long matchedOrderId;
    public long matchedOrderUid; // 0 for rejection
    public boolean matchedOrderCompleted; // false, except when matchedOrder is completely filled

    public long price; // 0 for rejection
    public long size;  // ? unmatched size for rejection
    public long timestamp; // same as activeOrder related event timestamp

    // reference to next event in chain
    public MatcherTradeEvent nextEvent;


    // testing only
    public MatcherTradeEvent copy() {
        MatcherTradeEvent evt = new MatcherTradeEvent();
        evt.eventType = this.eventType;
        evt.activeOrderId = this.activeOrderId;
        evt.activeOrderUid = this.activeOrderUid;
        evt.activeOrderCompleted = this.activeOrderCompleted;
        evt.activeOrderAction = this.activeOrderAction;
        evt.matchedOrderId = this.matchedOrderId;
        evt.matchedOrderUid = this.matchedOrderUid;
        evt.matchedOrderCompleted = this.matchedOrderCompleted;
        evt.price = this.price;
        evt.size = this.size;
        evt.timestamp = this.timestamp;
        return evt;
    }

    /**
     * Compare next events chain as well.
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof MatcherTradeEvent)) return false;
        MatcherTradeEvent other = (MatcherTradeEvent) o;
        return new EqualsBuilder()
                .append(symbol, other.symbol)
                .append(activeOrderId, other.activeOrderId)
                .append(activeOrderUid, other.activeOrderUid)
                .append(activeOrderCompleted, other.activeOrderCompleted)
                .append(activeOrderAction, other.activeOrderAction)
                .append(matchedOrderId, other.matchedOrderId)
                .append(matchedOrderUid, other.matchedOrderUid)
                .append(matchedOrderCompleted, other.matchedOrderCompleted)
                .append(price, other.price)
                .append(size, other.size)
                // ignore timestamp
                .append(nextEvent, other.nextEvent)
                .isEquals();
    }

    /**
     * Includes chaining events
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                symbol,
                activeOrderId,
                activeOrderUid,
                activeOrderCompleted,
                activeOrderAction,
                matchedOrderId,
                matchedOrderUid,
                matchedOrderCompleted,
                price,
                size,
                nextEvent);
    }

}
