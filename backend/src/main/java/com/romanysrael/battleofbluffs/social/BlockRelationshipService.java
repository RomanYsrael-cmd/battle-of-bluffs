package com.romanysrael.battleofbluffs.social;

import com.romanysrael.battleofbluffs.game.application.MatchJoinPolicy;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockRelationshipService implements MatchJoinPolicy {
    private final UserBlockRepository blocks;
    private final Clock clock;

    public BlockRelationshipService(UserBlockRepository blocks, Clock clock) {
        this.blocks = blocks;
        this.clock = clock;
    }

    @Override
    public boolean mayJoin(String existingPlayerId, String joiningPlayerId) {
        try {
            return !existsEitherDirection(
                    UUID.fromString(existingPlayerId),
                    UUID.fromString(joiningPlayerId));
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    public boolean existsEitherDirection(UUID firstUserId, UUID secondUserId) {
        return blocks.existsByIdBlockerIdAndIdBlockedId(firstUserId, secondUserId)
                || blocks.existsByIdBlockerIdAndIdBlockedId(secondUserId, firstUserId);
    }

    public boolean blockedBy(UUID blockerId, UUID blockedId) {
        return blocks.existsByIdBlockerIdAndIdBlockedId(blockerId, blockedId);
    }

    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new ChatException("INVALID_BLOCK", "You cannot block your own account.");
        }
        UserBlockEntity.UserBlockId id = new UserBlockEntity.UserBlockId(blockerId, blockedId);
        if (!blocks.existsById(id)) {
            blocks.save(new UserBlockEntity(blockerId, blockedId, clock.instant()));
        }
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        blocks.deleteById(new UserBlockEntity.UserBlockId(blockerId, blockedId));
    }
}
