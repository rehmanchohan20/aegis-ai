package ai.aegis.ml;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class MlModelDeploymentClient {
    private final RestClient client;
    private final String approvalToken;

    public MlModelDeploymentClient(RestClient.Builder builder,
                                   @Value("${aegis.ml.base-url:http://localhost:8000}") String baseUrl,
                                   @Value("${aegis.ml.deployment-approval-token:DISABLED}") String approvalToken) {
        this.client = builder.baseUrl(baseUrl).build();
        this.approvalToken = approvalToken;
    }

    public void activate(String modelName, String version) {
        if ("DISABLED".equals(approvalToken) || approvalToken.isBlank()) {
            throw new IllegalStateException("ML model deployment approval token is not configured");
        }
        client.post().uri("/models/activate").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model_name", modelName, "version", version, "approval_token", approvalToken))
                .retrieve().toBodilessEntity();
    }
}
