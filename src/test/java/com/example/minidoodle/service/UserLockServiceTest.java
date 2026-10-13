package com.example.minidoodle.service;

import com.example.minidoodle.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserLockServiceTest {
    private InMemoryRepositories db;

    @BeforeEach
    void setUp() { db = new InMemoryRepositories(); }

    @Test
    void immutableListDoesNotThrowNullPointerException() {
        UUID user = db.createUser("Ada");
        assertEquals(user, db.userLocks.lockUsers(List.of(user)).get(user).getId());
    }

    @Test
    void locksDistinctUsersInUuidOrder() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        db.userRows.put(first, new com.example.minidoodle.entity.UserEntity(first, "Ada"));
        db.userRows.put(second, new com.example.minidoodle.entity.UserEntity(second, "Grace"));

        db.userLocks.lockUsers(List.of(second, first, second));
        assertEquals(List.of(first, second), db.lockOrder);
    }

    @Test
    void missingUserThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        assertThrows(NotFoundException.class, () -> db.userLocks.lockUsers(List.of(missing)));
    }

    @Test
    void nullCollectionThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> db.userLocks.lockUsers(null));
    }

    @Test
    void nullElementThrowsIllegalArgumentException() {
        List<UUID> ids = new ArrayList<>(Arrays.asList(UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> db.userLocks.lockUsers(ids));
    }

    @Test
    void emptyCollectionDoesNotAcquireLocks() {
        assertTrue(db.userLocks.lockUsers(List.of()).isEmpty());
        assertTrue(db.lockOrder.isEmpty());
    }
}
