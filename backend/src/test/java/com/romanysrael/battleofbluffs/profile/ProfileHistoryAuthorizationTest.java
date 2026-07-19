package com.romanysrael.battleofbluffs.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.competition.RatingService;
import com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import com.romanysrael.battleofbluffs.game.application.InMemoryMatchRepository;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchViewMapper;
import com.romanysrael.battleofbluffs.game.persistence.MatchAggregateJpaRepository;
import com.romanysrael.battleofbluffs.user.AccountService;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileHistoryAuthorizationTest {
    @Test
    void activeMatchHistoryGivesParticipantSafeViewAndOutsiderOnlyPublicSummary() {
        MatchApplicationService matches = new MatchApplicationService(
                new InMemoryMatchRepository(), new PlayerMatchViewMapper());
        UUID hostId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        UUID matchId = matches.createMatch(new CreateMatchCommand(hostId.toString())).view().matchId();
        MatchAggregateJpaRepository aggregates = mock(MatchAggregateJpaRepository.class);
        when(aggregates.findById(matchId)).thenReturn(Optional.empty());
        ProfileService profiles = new ProfileService(
                mock(UserAccountRepository.class),
                mock(AccountService.class),
                matches,
                aggregates,
                mock(RatingService.class));

        ProfileService.MatchHistoryView participant = profiles.history(matchId, hostId);
        ProfileService.MatchHistoryView outsider = profiles.history(matchId, outsiderId);

        assertThat(participant.participant()).isTrue();
        assertThat(participant.participantView()).isNotNull();
        assertThat(outsider.participant()).isFalse();
        assertThat(outsider.participantView()).isNull();
        assertThat(outsider.summary().terminalResult()).isNull();
        assertThat(outsider.summary().acceptedMoveCount()).isNull();
    }
}
