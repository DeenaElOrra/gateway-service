package store.gateway.security;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class RouterValidator {

    /**
     * List of open API endpoints that don't require authentication
     * Format: "METHOD /path" or "METHOD /path/**" for deep matching
     */
    private static final List<String> OPEN_API_ENDPOINTS = List.of(
        "POST /auth/register",
        "POST /auth/login",
        "GET /health",
        "GET /info",
        "GET /"
    );

    /**
     * Predicate to check if a request requires authentication
     * Returns true if the route is secured (requires auth)
     * Returns false if the route is open (no auth needed)
     */
    public Predicate<ServerHttpRequest> isSecured = request -> 
        OPEN_API_ENDPOINTS.stream()
            .noneMatch(endpoint -> matchesEndpoint(request, endpoint));

    /**
     * Check if request matches an endpoint pattern
     */
    private boolean matchesEndpoint(ServerHttpRequest request, String endpoint) {
        String[] parts = endpoint.split(" ");
        if (parts.length != 2) {
            return false;
        }

        String method = parts[0];
        String path = parts[1];
        boolean isDeepMatch = path.endsWith("/**");

        // Check method match
        boolean methodMatches = "ANY".equalsIgnoreCase(method) 
            || request.getMethod().toString().equalsIgnoreCase(method);

        // Check path match
        String requestPath = request.getURI().getPath();
        boolean pathMatches;
        
        if (isDeepMatch) {
            String basePath = path.replace("/**", "");
            pathMatches = requestPath.startsWith(basePath);
        } else {
            pathMatches = requestPath.equals(path);
        }

        return methodMatches && pathMatches;
    }

}