package com.romanysrael.battleofbluffs.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {
    Optional<UserAccountEntity> findByNormalizedUsername(String normalizedUsername);

    Optional<UserAccountEntity> findByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedUsernameOrNormalizedEmail(String normalizedUsername, String normalizedEmail);
}
