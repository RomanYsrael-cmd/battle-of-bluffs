package com.romanysrael.battleofbluffs.social;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MatchChatMessageRepository extends JpaRepository<MatchChatMessageEntity, UUID> {
    @Query("select coalesce(max(message.sequenceNumber), 0) from MatchChatMessageEntity message "
            + "where message.matchId = :matchId")
    long maximumSequence(UUID matchId);

    List<MatchChatMessageEntity> findTop100ByMatchIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
            UUID matchId,
            long sequenceNumber);

    List<MatchChatMessageEntity> findTop100ByMatchIdOrderBySequenceNumberDesc(UUID matchId);
}
