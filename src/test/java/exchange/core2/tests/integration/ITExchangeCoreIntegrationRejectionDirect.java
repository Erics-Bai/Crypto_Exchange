package exchange.core2.tests.integration;

import exchange.core2.core.common.config.PerformanceConfiguration;

public class ITExchangeCoreIntegrationRejectionDirect extends ITExchangeCoreIntegrationRejection {

    @Override
    public PerformanceConfiguration getPerfCfg() {
        return PerformanceConfiguration.latencyPerformanceBuilder().build();
    }
}
