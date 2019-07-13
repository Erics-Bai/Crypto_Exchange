package org.openpredict.exchange.beans.reports;


import lombok.AllArgsConstructor;
import lombok.Getter;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;
import org.eclipse.collections.impl.map.mutable.primitive.IntLongHashMap;
import org.openpredict.exchange.beans.ReportType;
import org.openpredict.exchange.core.Utils;

import java.util.stream.Stream;

@AllArgsConstructor
@Getter
public class TotalCurrencyBalanceReportResult implements ReportResult {

    // currency -> balance
    private IntLongHashMap accountBalances;

    // currency -> balance
    private IntLongHashMap ordersBalances;

    private TotalCurrencyBalanceReportResult(final BytesIn bytesIn) {
        this.accountBalances = bytesIn.readBoolean() ? Utils.readIntLongHashMap(bytesIn) : null;
        this.ordersBalances = bytesIn.readBoolean() ? Utils.readIntLongHashMap(bytesIn) : null;
    }

    @Override
    public void writeMarshallable(final BytesOut bytes) {
        bytes.writeBoolean(accountBalances != null);
        if (accountBalances != null) {
            Utils.marshallIntLongHashMap(accountBalances, bytes);
        }

        bytes.writeBoolean(ordersBalances != null);
        if (ordersBalances != null) {
            Utils.marshallIntLongHashMap(ordersBalances, bytes);
        }
    }

    public static TotalCurrencyBalanceReportResult merge(final Stream<BytesIn> pieces) {
        return pieces
                .map(TotalCurrencyBalanceReportResult::new)
                .reduce(
                        new TotalCurrencyBalanceReportResult(null, null),
                        (a, b) -> new TotalCurrencyBalanceReportResult(Utils.mergeSum(a.accountBalances, b.accountBalances), Utils.mergeSum(a.ordersBalances, b.ordersBalances)));
    }

}
