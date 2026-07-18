package ai.aegis.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ExchangeOrderService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final boolean liveEnabled;
    private final String confirmationToken;

    public ExchangeOrderService(JdbcTemplate jdbc, ObjectMapper mapper,
                                @Value("${aegis.execution.live-enabled:false}") boolean liveEnabled,
                                @Value("${aegis.execution.confirmation-token:DISABLED}") String confirmationToken) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.liveEnabled = liveEnabled;
        this.confirmationToken = confirmationToken;
    }

    public OrderResult submit(OrderRequest request, String suppliedConfirmationToken) {
        validate(request);
        UUID auditId = UUID.randomUUID();
        String clientOrderId = request.clientOrderId() == null || request.clientOrderId().isBlank()
                ? "AEGIS-" + auditId : request.clientOrderId();
        Instant now = Instant.now();

        if (!liveEnabled) {
            persist(auditId, clientOrderId, request, "BLOCKED_LIVE_DISABLED", null,
                    Map.of("reason", "Live exchange execution is disabled"), now);
            return new OrderResult(auditId, clientOrderId, "BLOCKED_LIVE_DISABLED", null,
                    "Live execution is disabled by configuration");
        }
        if (!confirmationToken.equals(suppliedConfirmationToken)) {
            persist(auditId, clientOrderId, request, "BLOCKED_CONFIRMATION", null,
                    Map.of("reason", "Invalid execution confirmation token"), now);
            return new OrderResult(auditId, clientOrderId, "BLOCKED_CONFIRMATION", null,
                    "A valid execution confirmation token is required");
        }

        // A concrete venue adapter must replace this fail-closed branch before live trading is possible.
        persist(auditId, clientOrderId, request, "BLOCKED_NO_VENUE_ADAPTER", null,
                Map.of("reason", "No signed exchange adapter configured"), now);
        return new OrderResult(auditId, clientOrderId, "BLOCKED_NO_VENUE_ADAPTER", null,
                "No production exchange adapter is configured");
    }

    private void validate(OrderRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank())
            throw new IllegalArgumentException("symbol is required");
        if (!"LONG".equalsIgnoreCase(request.side()) && !"SHORT".equalsIgnoreCase(request.side()))
            throw new IllegalArgumentException("side must be LONG or SHORT");
        if (request.quantity() == null || request.quantity().signum() <= 0)
            throw new IllegalArgumentException("positive quantity is required");
        if (!"MARKET".equalsIgnoreCase(request.orderType()) && !"LIMIT".equalsIgnoreCase(request.orderType()))
            throw new IllegalArgumentException("orderType must be MARKET or LIMIT");
        if ("LIMIT".equalsIgnoreCase(request.orderType()) && (request.price() == null || request.price().signum() <= 0))
            throw new IllegalArgumentException("positive price is required for LIMIT orders");
    }

    private void persist(UUID id, String clientOrderId, OrderRequest request, String status,
                         String externalOrderId, Map<String, Object> payload, Instant now) {
        jdbc.update("""
                INSERT INTO execution.order_audit
                (id, client_order_id, venue, symbol, side, order_type, quantity, requested_price,
                 status, external_order_id, response_payload, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """, id, clientOrderId, request.venue(), request.symbol().toUpperCase(), request.side().toUpperCase(),
                request.orderType().toUpperCase(), request.quantity(), request.price(), status, externalOrderId,
                json(payload), Timestamp.from(now), Timestamp.from(now));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    public record OrderRequest(String clientOrderId, String venue, String symbol, String side,
                               String orderType, BigDecimal quantity, BigDecimal price) { }
    public record OrderResult(UUID auditId, String clientOrderId, String status,
                              String externalOrderId, String message) { }
}
