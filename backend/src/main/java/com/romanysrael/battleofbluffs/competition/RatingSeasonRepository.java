package com.romanysrael.battleofbluffs.competition;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RatingSeasonRepository extends JpaRepository<RatingSeasonEntity, UUID> {
    Optional<RatingSeasonEntity> findFirstByActiveTrueOrderByStartsAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select season
            from RatingSeasonEntity season
            where season.active = true
            order by season.startsAt desc
            """)
    List<RatingSeasonEntity> findActiveForUpdate(Pageable pageable);
}
