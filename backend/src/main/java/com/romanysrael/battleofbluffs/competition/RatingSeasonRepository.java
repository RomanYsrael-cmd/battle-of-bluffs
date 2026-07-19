package com.romanysrael.battleofbluffs.competition;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingSeasonRepository extends JpaRepository<RatingSeasonEntity, UUID> {
    Optional<RatingSeasonEntity> findFirstByActiveTrueOrderByStartsAtDesc();
}
