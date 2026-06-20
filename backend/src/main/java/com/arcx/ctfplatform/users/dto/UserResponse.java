package com.arcx.ctfplatform.users.dto;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.users.entity.User;

public record UserResponse(
        UUID id,
        String email,
        String username,
        String role,
        String status
) {
    @Component
    public static class Mapper implements IMapping<User, UserResponse> {
        @Override
        public UserResponse mapping(User from) {
            if (from == null) {
                return null;
            }
            return new UserResponse(
                    from.getId(),
                    from.getEmail(),
                    from.getUsername(),
                    from.getRole().name(),
                    from.getStatus().name()
            );
        }
    }
}
