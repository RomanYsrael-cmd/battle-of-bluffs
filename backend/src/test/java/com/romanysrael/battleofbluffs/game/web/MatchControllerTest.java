package com.romanysrael.battleofbluffs.game.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.romanysrael.battleofbluffs.game.application.InMemoryMatchRepository;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchViewMapper;
import com.romanysrael.battleofbluffs.game.domain.Rank;
import com.romanysrael.battleofbluffs.shared.config.SecurityConfig;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.AccountUserDetailsService;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(MatchController.class)
@Import({
        MatchApplicationService.class,
        InMemoryMatchRepository.class,
        PlayerMatchViewMapper.class,
        DevelopmentApiExceptionHandler.class,
        SecurityConfig.class
})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class MatchControllerTest {
    private static final AccountPrincipal HOST = principal("10000000-0000-4000-8000-000000000001", "host");
    private static final AccountPrincipal GUEST = principal("20000000-0000-4000-8000-000000000002", "guest");
    private static final AccountPrincipal OUTSIDER = principal("30000000-0000-4000-8000-000000000003", "outsider");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private AccountUserDetailsService userDetailsService;

    @Test
    void authenticatedIdentityExclusivelyControlsBothSeatsAndPrivateViews() throws Exception {
        JsonNode created = json.readTree(mvc.perform(post("/api/matches")
                        .with(user(HOST))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timerMode\":\"STANDARD_15_PLUS_5\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.view.requestingPlayerId").value(HOST.userId().toString()))
                .andExpect(jsonPath("$.view.mode").value("CASUAL"))
                .andExpect(jsonPath("$.view.timerMode").value("STANDARD_15_PLUS_5"))
                .andReturn().getResponse().getContentAsString());
        String matchId = created.get("matchId").stringValue();
        String roomCode = created.get("roomCode").stringValue();

        mvc.perform(post("/api/matches/join")
                        .with(user(GUEST))
                        .with(csrf())
                        .param("playerId", HOST.userId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "commandId", UUID.randomUUID(),
                                "roomCode", roomCode,
                                "expectedVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view.requestingPlayerId").value(GUEST.userId().toString()))
                .andExpect(jsonPath("$.view.requestingSide").value("PLAYER_TWO"));

        mvc.perform(get("/api/matches/{matchId}", matchId).with(user(HOST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestingSide").value("PLAYER_ONE"));
        mvc.perform(get("/api/matches/{matchId}", matchId).with(user(OUTSIDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_MATCH"));
    }

    @Test
    void anonymousAndMissingCsrfMatchCommandsAreRejectedAtTheSecurityBoundary() throws Exception {
        mvc.perform(post("/api/matches").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(post("/api/matches").with(user(HOST)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void productionRequestRecordsExposeNoPlayerIdInput() {
        assertThat(Arrays.stream(MatchController.JoinRequest.class.getRecordComponents()))
                .noneMatch(component -> component.getName().equals("playerId"));
        assertThat(Arrays.stream(MatchController.CommandRequest.class.getRecordComponents()))
                .noneMatch(component -> component.getName().equals("playerId"));
        assertThat(Arrays.stream(MatchController.FormationRequest.class.getRecordComponents()))
                .noneMatch(component -> component.getName().equals("playerId"));
        assertThat(Arrays.stream(MatchController.MoveRequest.class.getRecordComponents()))
                .noneMatch(component -> component.getName().equals("playerId"));
    }

    @Test
    void insecureDevelopmentControllerRequiresExplicitDevProfile() {
        Profile profile = DevelopmentMatchController.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("dev");
    }

    @Test
    void authenticatedRestFlowRunsFromPrivateRoomCreationThroughTerminalDisclosure() throws Exception {
        JsonNode created = body(mvc.perform(post("/api/matches")
                        .with(user(HOST)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String matchId = created.get("matchId").stringValue();
        String roomCode = created.get("roomCode").stringValue();
        JsonNode joined = body(mvc.perform(post("/api/matches/join")
                        .with(user(GUEST)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "commandId", UUID.randomUUID(),
                                "roomCode", roomCode,
                                "expectedVersion", 1))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long version = joined.get("version").asLong();
        version = formation(matchId, HOST, version, 0);
        version = formation(matchId, GUEST, version, 5);
        version = command("/api/matches/{id}/lock", matchId, HOST, version);
        version = command("/api/matches/{id}/lock", matchId, GUEST, version);

        JsonNode hostView = body(mvc.perform(get("/api/matches/{id}", matchId).with(user(HOST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("ACTIVE"))
                .andExpect(jsonPath("$.opponentPieces[0].rank").doesNotExist())
                .andReturn().getResponse().getContentAsString());
        boolean hostMoves = hostView.get("currentPlayer").stringValue().equals("PLAYER_ONE");
        AccountPrincipal mover = hostMoves ? HOST : GUEST;
        AccountPrincipal resigner = hostMoves ? GUEST : HOST;
        int sourceRow = hostMoves ? 2 : 5;
        int destinationRow = hostMoves ? 3 : 4;
        JsonNode moved = body(mvc.perform(post("/api/matches/{id}/moves", matchId)
                        .with(user(mover)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "commandId", UUID.randomUUID(),
                                "expectedVersion", version,
                                "source", Map.of("row", sourceRow, "column", 0),
                                "destination", Map.of("row", destinationRow, "column", 0)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        command("/api/matches/{id}/resign", matchId, resigner, moved.get("version").asLong());

        mvc.perform(get("/api/matches/{id}", matchId).with(user(HOST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("TERMINAL"))
                .andExpect(jsonPath("$.postMatchPieces.length()").value(42));
        mvc.perform(get("/api/matches/{id}", matchId).with(user(GUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("TERMINAL"))
                .andExpect(jsonPath("$.postMatchPieces.length()").value(42));
    }

    private long formation(String matchId, AccountPrincipal player, long version, int startRow) throws Exception {
        List<Rank> ranks = new ArrayList<>(List.of(
                Rank.FIVE_STAR_GENERAL, Rank.FOUR_STAR_GENERAL, Rank.THREE_STAR_GENERAL,
                Rank.TWO_STAR_GENERAL, Rank.ONE_STAR_GENERAL, Rank.COLONEL,
                Rank.LIEUTENANT_COLONEL, Rank.MAJOR, Rank.CAPTAIN, Rank.FIRST_LIEUTENANT,
                Rank.SECOND_LIEUTENANT, Rank.SERGEANT, Rank.SPY, Rank.SPY, Rank.FLAG));
        for (int index = 0; index < 6; index++) {
            ranks.add(Rank.PRIVATE);
        }
        List<Map<String, Object>> pieces = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            pieces.add(Map.of(
                    "pieceId", UUID.randomUUID(),
                    "rank", ranks.get(index),
                    "row", startRow + index / 9,
                    "column", index % 9));
        }
        JsonNode response = body(mvc.perform(put("/api/matches/{id}/formation", matchId)
                        .with(user(player)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "commandId", UUID.randomUUID(),
                                "expectedVersion", version,
                                "pieces", pieces))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return response.get("version").asLong();
    }

    private long command(
            String url,
            String matchId,
            AccountPrincipal player,
            long version) throws Exception {
        JsonNode response = body(mvc.perform(post(url, matchId)
                        .with(user(player)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "commandId", UUID.randomUUID(),
                                "expectedVersion", version))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return response.get("version").asLong();
    }

    private JsonNode body(String value) throws Exception {
        return json.readTree(value);
    }

    private static AccountPrincipal principal(String id, String username) {
        return new AccountPrincipal(
                UUID.fromString(id), username, username, AccountStatus.ACTIVE, "{noop}unused");
    }
}
