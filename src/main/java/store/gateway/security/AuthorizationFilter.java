package store.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class AuthorizationFilter implements GlobalFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer";
    private static final String AUTH_SERVICE_SOLVE_URL = "http://auth:8080/auth/solve";
    private static final String ACCOUNT_ID_HEADER = "id-account";

    @Autowired
    private RouterValidator routerValidator;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logger.debug("Processing request through authorization filter");
        
        ServerHttpRequest request = exchange.getRequest();

        // Check if route requires authentication
        if (!routerValidator.isSecured.test(request)) {
            logger.debug("Route is not secured, bypassing authentication");
            return chain.filter(exchange);
        }

        logger.debug("Route is secured, validating authorization");

        // Check if Authorization header is present
        if (isAuthorizationMissing(request)) {
            logger.warn("Authorization header missing for secured route");
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authorization header is required"
            );
        }

        // Extract and validate token
        String authHeader = getAuthorizationHeader(request);
        String token = extractToken(authHeader);

        // Validate token with auth service and continue
        return validateTokenAndContinue(exchange, chain, token);
    }

    private boolean isAuthorizationMissing(ServerHttpRequest request) {
        return !request.getHeaders().containsKey(AUTHORIZATION_HEADER);
    }

    private String getAuthorizationHeader(ServerHttpRequest request) {
        return request.getHeaders()
            .getOrEmpty(AUTHORIZATION_HEADER)
            .get(0);
    }

    private String extractToken(String authHeader) {
        logger.debug("Extracting token from Authorization header");

        String[] parts = authHeader.trim().split(" ");

        if (parts.length != 2) {
            logger.warn("Invalid Authorization header format: expected 2 parts, got {}", parts.length);
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Authorization header format must be: 'Bearer {token}'"
            );
        }

        if (!BEARER_PREFIX.equalsIgnoreCase(parts[0])) {
            logger.warn("Invalid Authorization type: expected 'Bearer', got '{}'", parts[0]);
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Authorization type must be 'Bearer'"
            );
        }

        logger.debug("Token extracted successfully");
        return parts[1];
    }

    private Mono<Void> validateTokenAndContinue(
        ServerWebExchange exchange, 
        GatewayFilterChain chain, 
        String token
    ) {
        logger.debug("Validating token with auth service");

        return webClientBuilder
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
            .post()
            .uri(AUTH_SERVICE_SOLVE_URL)
            .bodyValue(TokenOut.builder().token(token).build())
            .retrieve()
            .toEntity(SolveOut.class)
            .flatMap(response -> {
                if (response.hasBody() && response.getBody() != null) {
                    SolveOut solveOut = response.getBody();
                    String accountId = solveOut.idAccount();
                    
                    logger.debug("Token validated successfully for account: {}", accountId);
                    
                    // Add account ID to request headers
                    ServerWebExchange modifiedExchange = addAccountIdHeader(exchange, accountId);
                    
                    return chain.filter(modifiedExchange);
                } else {
                    logger.warn("Token validation failed: empty response from auth service");
                    throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid or expired token"
                    );
                }
            })
            .onErrorResume(error -> {
                if (error instanceof ResponseStatusException) {
                    return Mono.error(error);
                }
                
                logger.error("Error validating token with auth service", error);
                return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token validation failed"
                ));
            });
    }

    private ServerWebExchange addAccountIdHeader(ServerWebExchange exchange, String accountId) {
        logger.debug("Adding {} header with value: {}", ACCOUNT_ID_HEADER, accountId);

        return exchange.mutate()
            .request(exchange.getRequest()
                .mutate()
                .header(ACCOUNT_ID_HEADER, accountId)
                .build())
            .build();
    }

}