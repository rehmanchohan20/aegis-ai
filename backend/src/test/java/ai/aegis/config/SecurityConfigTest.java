package ai.aegis.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {
    private final SecurityConfig.ApiKeyFilter filter = new SecurityConfig.ApiKeyFilter("viewer-key", "admin-key");

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void viewerDoesNotReceiveAdminRole() throws Exception {
        authenticate("viewer-key");
        var roles = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertTrue(roles.stream().anyMatch(role -> role.getAuthority().equals("ROLE_VIEWER")));
        assertFalse(roles.stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void adminReceivesBothRoles() throws Exception {
        authenticate("admin-key");
        var roles = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertTrue(roles.stream().anyMatch(role -> role.getAuthority().equals("ROLE_VIEWER")));
        assertTrue(roles.stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN")));
    }

    private void authenticate(String key) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dashboard");
        request.addHeader("X-AEGIS-API-KEY", key);
        filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class));
    }
}
