package com.romanysrael.battleofbluffs.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlockRelationshipServiceTest {
    @Test
    void casualJoinPolicyRejectsABlockInEitherDirection() {
        UserBlockRepository repository = org.mockito.Mockito.mock(UserBlockRepository.class);
        UUID hostId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        when(repository.existsByIdBlockerIdAndIdBlockedId(hostId, guestId)).thenReturn(false);
        when(repository.existsByIdBlockerIdAndIdBlockedId(guestId, hostId)).thenReturn(true);
        BlockRelationshipService relationships = new BlockRelationshipService(
                repository, Clock.systemUTC());

        assertThat(relationships.mayJoin(hostId.toString(), guestId.toString())).isFalse();
    }
}
