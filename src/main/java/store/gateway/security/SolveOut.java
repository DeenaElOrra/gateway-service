package store.gateway.security;

import lombok.Builder;

@Builder
public record SolveOut(
    String idAccount
) {}