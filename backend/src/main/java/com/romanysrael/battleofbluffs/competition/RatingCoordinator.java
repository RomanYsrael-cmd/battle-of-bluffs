package com.romanysrael.battleofbluffs.competition;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RatingCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(RatingCoordinator.class);

    private final MatchApplicationService matches;
    private final RatingService ratings;

    public RatingCoordinator(MatchApplicationService matches, RatingService ratings) {
        this.matches = matches;
        this.ratings = ratings;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverMissingRatings() {
        applyPendingRatings();
    }

    @Scheduled(fixedDelayString = "${app.rating.reconcile-ms:1000}")
    public void applyPendingRatings() {
        matches.summaries().forEach(match -> {
            try {
                ratings.apply(match);
            } catch (RuntimeException exception) {
                LOGGER.error("Could not reconcile rating for match {}", match.matchId(), exception);
            }
        });
    }
}
