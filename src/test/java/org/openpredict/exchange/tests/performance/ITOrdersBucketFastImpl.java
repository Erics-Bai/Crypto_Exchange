package org.openpredict.exchange.tests.performance;

import org.openpredict.exchange.core.orderbook.IOrdersBucket;
import org.openpredict.exchange.core.orderbook.OrdersBucketFastImpl;

public class ITOrdersBucketFastImpl extends ITOrdersBucketBase {

    @Override
    protected IOrdersBucket createNewOrdersBucket() {
        return new OrdersBucketFastImpl();
    }
}
