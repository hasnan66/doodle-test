package com.example.minidoodle.service;

import com.example.minidoodle.entity.UserEntity;
import com.example.minidoodle.exception.NotFoundException;
import com.example.minidoodle.repository.UserJpaRepository;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** All user-affecting writes must acquire user-row locks in ascending UUID order. */
@Component
public class UserLockService {
    private final UserJpaRepository users;

    public UserLockService(UserJpaRepository users) { this.users = users; }

    public Map<UUID, UserEntity> lockUsers(Collection<UUID> ids) {
        if (ids == null || ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Participant IDs cannot be null");
        }
        Map<UUID, UserEntity> locked = new LinkedHashMap<>();
        for (UUID id : ids.stream().distinct().sorted().toList()) {
            locked.put(id, users.lockById(id)
                    .orElseThrow(() -> new NotFoundException("User not found: " + id)));
        }
        return locked;
    }
}
