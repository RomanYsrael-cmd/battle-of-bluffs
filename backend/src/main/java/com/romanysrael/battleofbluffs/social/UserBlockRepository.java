package com.romanysrael.battleofbluffs.social;

import com.romanysrael.battleofbluffs.social.UserBlockEntity.UserBlockId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlockEntity, UserBlockId> {
    boolean existsByIdBlockerIdAndIdBlockedId(UUID blockerId, UUID blockedId);
}
