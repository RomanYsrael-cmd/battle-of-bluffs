package com.romanysrael.battleofbluffs.competition;

import com.romanysrael.battleofbluffs.competition.PlayerRatingEntity.PlayerRatingId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRatingRepository extends JpaRepository<PlayerRatingEntity, PlayerRatingId> {
    Optional<PlayerRatingEntity> findByIdUserIdAndIdSeasonId(UUID userId, UUID seasonId);

    List<PlayerRatingEntity> findByIdSeasonIdOrderByRatingDescRatedGamesDescIdUserIdAsc(UUID seasonId);

    List<PlayerRatingEntity> findByIdUserId(UUID userId);
}
