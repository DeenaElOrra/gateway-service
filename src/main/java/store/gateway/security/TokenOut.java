package store.gateway.security;

import lombok.Builder;

@Builder
public record TokenOut(
    String token
) {}