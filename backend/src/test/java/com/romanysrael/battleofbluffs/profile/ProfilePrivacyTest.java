package com.romanysrael.battleofbluffs.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProfilePrivacyTest {
    @Test
    void publicProfileContractContainsNoPrivateAccountOrModerationFields() {
        assertThat(Arrays.stream(ProfileService.PublicProfileView.class.getRecordComponents())
                        .map(component -> component.getName().toLowerCase()))
                .noneMatch(name -> name.contains("email")
                        || name.contains("password")
                        || name.contains("token")
                        || name.contains("session")
                        || name.contains("report")
                        || name.contains("block"));
    }

    @Test
    void nonparticipantHistoryContractSeparatesPublicSummaryFromParticipantView() {
        assertThat(Arrays.stream(ProfileService.PublicMatchSummary.class.getRecordComponents())
                        .map(component -> component.getName().toLowerCase()))
                .noneMatch(name -> name.contains("piece")
                        || name.contains("rank")
                        || name.contains("formation")
                        || name.contains("roomcode"));
    }
}
