package ai.aegis.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyFilter apiKeyFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .addFilterBefore(apiKeyFilter, AbstractPreAuthenticatedProcessingFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/api/v1/public/**").permitAll()
                        .requestMatchers("/api/v1/admin/**", "/api/v1/execution/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
                .build();
    }

    @Bean
    ApiKeyFilter apiKeyFilter(@Value("${aegis.security.viewer-key}") String viewerKey,
                              @Value("${aegis.security.admin-key}") String adminKey) {
        return new ApiKeyFilter(viewerKey, adminKey);
    }

    static final class ApiKeyFilter extends OncePerRequestFilter {
        private final String viewerKey;
        private final String adminKey;
        ApiKeyFilter(String viewerKey, String adminKey) { this.viewerKey = viewerKey; this.adminKey = adminKey; }

        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                  FilterChain chain) throws ServletException, IOException {
            String key = request.getHeader("X-AEGIS-API-KEY");
            if (key != null) {
                if (matches(key, adminKey)) authenticate("admin", List.of("ROLE_ADMIN", "ROLE_VIEWER"));
                else if (matches(key, viewerKey)) authenticate("viewer", List.of("ROLE_VIEWER"));
                else { response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid API key"); return; }
            }
            chain.doFilter(request, response);
        }

        private void authenticate(String principal, List<String> roles) {
            var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
        }

        private boolean matches(String supplied, String configured) {
            return MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),
                    configured.getBytes(StandardCharsets.UTF_8));
        }
    }
}
