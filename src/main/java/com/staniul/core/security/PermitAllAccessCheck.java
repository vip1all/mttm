package com.staniul.core.security;

public class PermitAllAccessCheck <T> implements AccessCheck<T> {
    @Override
    public Boolean apply(T client) {
        return true;
    }
}
