package exchange.core2.core.common.config;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;


/**
 * Order processing configuration
 */
@AllArgsConstructor
@Getter
@Builder
public final class OrdersProcessingConfiguration {

    public static OrdersProcessingConfiguration DEFAULT = OrdersProcessingConfiguration.builder()
            .riskProcessingMode(RiskProcessingMode.FULL_PER_CURRENCY)
            .build();

    private final RiskProcessingMode riskProcessingMode;

    public enum RiskProcessingMode {
        // risk processing is on, every currency/asset account is checked independently
        FULL_PER_CURRENCY,

        // risk processing is off, any orders accepted and placed
        NO_RISK_PROCESSING
    }
}
