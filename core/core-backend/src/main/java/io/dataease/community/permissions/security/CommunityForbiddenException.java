package io.dataease.community.permissions.security;

public class CommunityForbiddenException extends RuntimeException {

    public CommunityForbiddenException(String message) {
        super(message);
    }
}
