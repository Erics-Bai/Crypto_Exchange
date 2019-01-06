package org.openpredict.exchange.beans;


import com.google.common.base.Objects;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.openpredict.exchange.beans.cmd.OrderCommand;
import org.openpredict.exchange.beans.cmd.OrderCommandType;

/**
 * Extending OrderCommand allows to avoid creating new objects
 * for instantly matching orders (MARKET or marketable LIMIT orders)
 * as well as use same code for matching moved orders
 * <p>
 * No external references allowed to such object - order objects only live inside OrderBook.
 */
@NoArgsConstructor
public class Order extends OrderCommand {

    public long filled;

    @Builder(builderMethodName = "orderBuilder", builderClassName = "OrderBuilder")
    public Order(OrderCommandType command, long orderId, int symbol, long price, long size, OrderAction action, OrderType orderType,
                 long uid, long timestamp, long filled) {
        //super(command, orderId, symbol, price, size, action, orderType, uid, timestamp, 0, null, null);
        super(command, orderId, symbol, price, size, action, orderType, uid, timestamp, 0, null, null, null);
        this.filled = filled;
    }

    @Override
    public String toString() {
        return "[" + orderId + " " + (action == OrderAction.ASK ? 'A' : 'B') + (orderType == OrderType.MARKET ? 'M' : 'L')
                + price + ":" + size + "F" + filled + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(orderId, action, orderType, price, size, filled, symbol, uid);
    }


    /**
     * timestamp is not included into hashCode() and equals() for repeatable results
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof Order)) return false;

        Order other = (Order) o;
        return new EqualsBuilder()
                .append(orderId, other.orderId)
                .append(action, other.action)
                .append(orderType, other.orderType)
                .append(price, other.price)
                .append(size, other.size)
                .append(filled, other.filled)
                .append(symbol, other.symbol)
                .append(uid, other.uid)
                //.append(timestamp, other.timestamp)
                .isEquals();
    }

}
