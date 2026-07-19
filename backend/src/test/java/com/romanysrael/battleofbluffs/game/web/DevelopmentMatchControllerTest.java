package com.romanysrael.battleofbluffs.game.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import tools.jackson.databind.*;
import com.romanysrael.battleofbluffs.game.application.*;
import com.romanysrael.battleofbluffs.game.domain.Rank;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DevelopmentMatchController.class)
@Import({MatchApplicationService.class,InMemoryMatchRepository.class,PlayerMatchViewMapper.class,DevelopmentApiExceptionHandler.class})
class DevelopmentMatchControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;

    @Test void completeDevelopmentFlow() throws Exception {
        JsonNode created=body(mvc.perform(post("/api/dev/matches").contentType(MediaType.APPLICATION_JSON).content("{\"playerId\":\"alice\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1)).andReturn().getResponse().getContentAsString());
        String id=created.get("matchId").stringValue(), code=created.get("roomCode").stringValue();
        JsonNode joined=body(mvc.perform(post("/api/dev/matches/{id}/join",id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("commandId",UUID.randomUUID(),"roomCode",code,"playerId","bob","expectedVersion",1))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long version=joined.get("version").asLong();
        version=formation(id,"alice",version,0); version=formation(id,"bob",version,5);
        version=command("/api/dev/matches/{id}/lock",id,"alice",version);
        version=command("/api/dev/matches/{id}/lock",id,"bob",version);
        JsonNode alice=body(mvc.perform(get("/api/dev/matches/{id}",id).param("playerId","alice"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phase").value("ACTIVE"))
                .andExpect(jsonPath("$.opponentPieces[0].rank").doesNotExist()).andReturn().getResponse().getContentAsString());
        String mover=alice.get("currentPlayer").stringValue().equals("PLAYER_ONE")?"alice":"bob";
        int sourceRow=mover.equals("alice")?2:5, destinationRow=mover.equals("alice")?3:4;
        JsonNode moved=body(mvc.perform(post("/api/dev/matches/{id}/moves",id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "commandId",UUID.randomUUID(),"playerId",mover,"expectedVersion",version,
                "source",Map.of("row",sourceRow,"column",0),"destination",Map.of("row",destinationRow,"column",0)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String resigner=mover.equals("alice")?"bob":"alice";
        command("/api/dev/matches/{id}/resign",id,resigner,moved.get("version").asLong());
        mvc.perform(get("/api/dev/matches/{id}",id).param("playerId",resigner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("TERMINAL")).andExpect(jsonPath("$.postMatchPieces.length()").value(42));
    }

    @Test
    void apiErrorsUseConsistentStatusesAndStructuredBodies() throws Exception {
        assertStructuredError(mvc.perform(post("/api/dev/matches/{id}/join", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{")), 400, "INVALID_REQUEST");

        JsonNode created = body(mvc.perform(post("/api/dev/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"alice-errors\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String matchId = created.get("matchId").stringValue();
        String roomCode = created.get("roomCode").stringValue();

        assertStructuredError(mvc.perform(get("/api/dev/matches/{id}", UUID.randomUUID())
                        .param("playerId", "alice-errors")), 404, "MATCH_NOT_FOUND");
        assertStructuredError(mvc.perform(get("/api/dev/matches/{id}", matchId)
                        .param("playerId", "mallory")), 403, "PLAYER_NOT_IN_MATCH");
        assertStructuredError(mvc.perform(post("/api/dev/matches/{id}/join", matchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "commandId", UUID.randomUUID(),
                                "roomCode", roomCode,
                                "playerId", "bob-errors",
                                "expectedVersion", 2)))),
                409, "STALE_VERSION");

        List<Map<String, Object>> invalidPieces = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            invalidPieces.add(Map.of(
                    "pieceId", UUID.randomUUID(),
                    "rank", Rank.PRIVATE,
                    "row", index / 9,
                    "column", index % 9));
        }
        assertStructuredError(mvc.perform(put("/api/dev/matches/{id}/formation", matchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "commandId", UUID.randomUUID(),
                                "playerId", "alice-errors",
                                "expectedVersion", 1,
                                "pieces", invalidPieces)))),
                422, "INVALID_FORMATION");
    }

    @Test
    void joinByRoomCodeDoesNotRequireTheGuestToKnowTheMatchId() throws Exception {
        JsonNode created = body(mvc.perform(post("/api/dev/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"room-code-host\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/dev/matches/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "commandId", UUID.randomUUID(),
                                "roomCode", created.get("roomCode").stringValue(),
                                "playerId", "room-code-guest",
                                "expectedVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(created.get("matchId").stringValue()))
                .andExpect(jsonPath("$.playerId").value("room-code-guest"))
                .andExpect(jsonPath("$.view.requestingSide").value("PLAYER_TWO"));
    }

    private void assertStructuredError(
            org.springframework.test.web.servlet.ResultActions action,
            int expectedStatus,
            String expectedCode) throws Exception {
        action.andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
    private long formation(String id,String player,long version,int start) throws Exception {
        List<Rank> ranks=new ArrayList<>(List.of(Rank.FIVE_STAR_GENERAL,Rank.FOUR_STAR_GENERAL,Rank.THREE_STAR_GENERAL,Rank.TWO_STAR_GENERAL,Rank.ONE_STAR_GENERAL,Rank.COLONEL,Rank.LIEUTENANT_COLONEL,Rank.MAJOR,Rank.CAPTAIN,Rank.FIRST_LIEUTENANT,Rank.SECOND_LIEUTENANT,Rank.SERGEANT,Rank.SPY,Rank.SPY,Rank.FLAG));
        for(int i=0;i<6;i++)ranks.add(Rank.PRIVATE); List<Map<String,Object>> pieces=new ArrayList<>();
        for(int i=0;i<21;i++)pieces.add(Map.of("pieceId",UUID.randomUUID(),"rank",ranks.get(i),"row",start+i/9,"column",i%9));
        JsonNode response=body(mvc.perform(put("/api/dev/matches/{id}/formation",id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("commandId",UUID.randomUUID(),"playerId",player,"expectedVersion",version,"pieces",pieces))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); return response.get("version").asLong();
    }
    private long command(String url,String id,String player,long version)throws Exception {JsonNode r=body(mvc.perform(post(url,id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("commandId",UUID.randomUUID(),"playerId",player,"expectedVersion",version)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());return r.get("version").asLong();}
    private JsonNode body(String value)throws Exception{return json.readTree(value);}
}
