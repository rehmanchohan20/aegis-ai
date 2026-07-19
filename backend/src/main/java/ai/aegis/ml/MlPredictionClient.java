package ai.aegis.ml;

import ai.aegis.feature.FeatureSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MlPredictionClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MlPredictionClient(RestClient.Builder builder,
                              ObjectMapper objectMapper,
                              @Value("${aegis.ml.base-url:http://localhost:8000}") String baseUrl) {
        this.restClient = MlRestClientFactory.create(builder, baseUrl);
        this.objectMapper = objectMapper;
    }

    public MlPrediction predict(FeatureSnapshot snapshot) {
        Map<String, BigDecimal> features = new LinkedHashMap<>();
        snapshot.features().forEach((name, value) -> {
            if (value.usable()) features.put(name, value.value());
        });
        if (features.isEmpty()) throw new IllegalArgumentException("No usable features available for ML prediction");

        Map<String, Object> body = Map.of("features", features);
        return restClient.post()
                .uri("/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MlPrediction.class);
    }
}
