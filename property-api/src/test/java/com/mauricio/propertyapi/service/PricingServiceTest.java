package com.mauricio.propertyapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mauricio.pricing.contract.JsonRpcResponse;
import com.mauricio.propertyapi.client.PricingClient;
import com.mauricio.propertyapi.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    PricingClient pricingClient;

    @InjectMocks
    PricingService pricingService;

    @Nested
    @DisplayName("getValuation")
    class GetValuation {

        @Test
        @DisplayName("should return valuation data on successful RPC call")
        void shouldReturnValuationData() {
            // Simular respuesta exitosa del pricing-api
            var resultMap = Map.of(
                    "estimatedValue", 225000,
                    "pricePerSqm", 2500,
                    "marketTrend", "high"
            );
            var rpcResponse = JsonRpcResponse.success(resultMap, 1);

            when(pricingClient.call(any())).thenReturn(rpcResponse);

            var result = pricingService.getValuation("Buenos Aires", 3);

            assertThat(result.estimatedValue()).isEqualByComparingTo(BigDecimal.valueOf(225000));
            assertThat(result.pricePerSqm()).isEqualByComparingTo(BigDecimal.valueOf(2500));
            assertThat(result.marketTrend()).isEqualTo("high");
        }

        @Test
        @DisplayName("should throw BusinessRuleException when pricing-api returns error")
        void shouldThrowOnPricingError() {
            var rpcResponse = JsonRpcResponse.error(-32602, "Invalid params", 1);

            when(pricingClient.call(any())).thenReturn(rpcResponse);

            assertThatThrownBy(() -> pricingService.getValuation("Unknown", 3))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Pricing service error");
        }
    }
}
