package com.romanysrael.battleofbluffs.competition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingChangeRepository extends JpaRepository<RatingChangeEntity, UUID> {
    boolean existsByMatchId(UUID matchId);

    Optional<RatingChangeEntity> findByMatchIdAndUserId(UUID matchId, UUID userId);

    List<RatingChangeEntity> findByUserId(UUID userId);
}
