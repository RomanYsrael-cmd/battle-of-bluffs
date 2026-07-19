package com.romanysrael.battleofbluffs.game.application;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class MatchLifecycleCoordinator {
    private final MatchApplicationService matches;

    public MatchLifecycleCoordinator(MatchApplicationService matches) {
        this.matches = matches;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPersistedDeadlines() {
        matches.recoverAfterRestart();
    }

    @Scheduled(fixedDelayString = "${app.match.deadline-evaluation-ms:250}")
    public void evaluateDeadlines() {
        matches.evaluateAllDeadlines();
    }

    @Scheduled(fixedDelayString = "${app.match.timer-sync-ms:30000}")
    public void publishTimerSyncs() {
        matches.publishTimerSyncs();
    }
}
