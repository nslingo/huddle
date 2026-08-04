package com.huddle.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Primary lookup for sign-in: {@code google_sub} is Google's stable, immutable user id. */
    Optional<User> findByGoogleSub(String googleSub);

    /** Lookup by the external identifier, for resolving an authenticated principal to a row. */
    Optional<User> findByPublicId(UUID publicId);
}