package com.romanysrael.battleofbluffs.game.application;

import static com.romanysrael.battleofbluffs.game.application.Commands.*;
import static org.junit.jupiter.api.Assertions.*;

import com.romanysrael.battleofbluffs.game.domain.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchApplicationServiceTest {
    MatchApplicationService service;
    @BeforeEach void setUp(){service=new MatchApplicationService(new InMemoryMatchRepository(),new PlayerMatchViewMapper());}

    @Test void createJoinLookupAndSeatRules() {
        MatchCommandResult created=service.createMatch(new CreateMatchCommand("alice"));
        assertEquals(1,created.version()); assertTrue(created.view().playerOneOccupied()); assertFalse(created.view().playerTwoOccupied());
        UUID id=created.view().matchId(); String code=created.view().roomCode();
        MatchCommandResult joined=service.joinMatch(id,new JoinMatchCommand(UUID.randomUUID(),code,"bob",1));
        assertEquals(id,service.getViewByRoomCode(code,"alice").matchId()); assertTrue(joined.view().playerTwoOccupied());
        assertEquals(MatchErrorCode.MATCH_FULL, failure(()->service.joinMatch(id,new JoinMatchCommand(UUID.randomUUID(),code,"charlie",2))).code());
        MatchApplicationService other=new MatchApplicationService(new InMemoryMatchRepository(),new PlayerMatchViewMapper());
        MatchCommandResult one=other.createMatch(new CreateMatchCommand("same"));
        assertEquals(MatchErrorCode.INVALID_ROOM_STATE,failure(()->other.joinMatch(one.view().matchId(),new JoinMatchCommand(UUID.randomUUID(),one.view().roomCode(),"same",1))).code());
        assertEquals(MatchErrorCode.PLAYER_NOT_IN_MATCH,failure(()->service.getView(id,"unknown")).code());
    }

    @Test void blockRelationshipPreventsCasualJoinWithoutChangingTheMatch() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));
        service.setJoinPolicy((existingPlayerId, joiningPlayerId) -> false);

        MatchApplicationException rejection = failure(() -> service.joinMatch(
                created.view().matchId(),
                new JoinMatchCommand(
                        UUID.randomUUID(), created.view().roomCode(), "blocked-guest", 1)));

        assertEquals(MatchErrorCode.BLOCKED_RELATION, rejection.code());
        PlayerMatchView unchanged = service.getView(created.view().matchId(), "host");
        assertEquals(1, unchanged.version());
        assertFalse(unchanged.playerTwoOccupied());
    }

    @Test void validatesFormationAndAllowsReplacementOnlyBeforeLock() {
        Fixture f=fixture();
        List<FormationPiece> invalid=new ArrayList<>(formation(PlayerSide.PLAYER_ONE)); invalid.set(0,new FormationPiece(invalid.get(0).pieceId(),Rank.PRIVATE,invalid.get(0).position()));
        assertEquals(MatchErrorCode.INVALID_FORMATION,failure(()->service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(),f.id,"a",2,invalid))).code());
        List<FormationPiece> outside=new ArrayList<>(formation(PlayerSide.PLAYER_ONE)); outside.set(0,new FormationPiece(outside.get(0).pieceId(),outside.get(0).rank(),new Position(3,0)));
        assertEquals(MatchErrorCode.INVALID_FORMATION,failure(()->service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(),f.id,"a",2,outside))).code());
        List<FormationPiece> duplicate=new ArrayList<>(formation(PlayerSide.PLAYER_ONE)); duplicate.set(1,new FormationPiece(duplicate.get(1).pieceId(),duplicate.get(1).rank(),duplicate.get(0).position()));
        assertEquals(MatchErrorCode.INVALID_FORMATION,failure(()->service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(),f.id,"a",2,duplicate))).code());
        service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(),f.id,"a",2,formation(PlayerSide.PLAYER_ONE)));
        List<FormationPiece> replacement=new ArrayList<>(formation(PlayerSide.PLAYER_ONE)); Collections.swap(replacement,0,1);
        service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(),f.id,"a",3,replacement));
        service.lockFormation(new LockFormationCommand(UUID.randomUUID(),f.id,"a",4));
        assertEquals(MatchErrorCode.ALREADY_LOCKED,failure(()->service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(),f.id,"a",5,formation(PlayerSide.PLAYER_ONE)))).code());
    }

    @Test void startsOnlyAfterBothLocksAndViewsKeepRanksPrivateUntilTerminal() {
        Fixture f=fixture(); long v=submitBoth(f,2);
        service.lockFormation(new LockFormationCommand(UUID.randomUUID(),f.id,"a",v++));
        assertEquals(MatchPhase.FORMATION,service.getView(f.id,"a").phase());
        service.lockFormation(new LockFormationCommand(UUID.randomUUID(),f.id,"b",v));
        PlayerMatchView a=service.getView(f.id,"a"), b=service.getView(f.id,"b");
        assertEquals(MatchPhase.ACTIVE,a.phase()); assertNotNull(a.currentPlayer());
        assertEquals(21,a.ownPieces().size()); assertEquals(21,a.opponentPieces().size());
        assertEquals(21,b.ownPieces().size()); assertEquals(21,b.opponentPieces().size());
        assertTrue(a.opponentPieces().stream().noneMatch(p->p.id().toString().toLowerCase().contains("flag")));
        service.resign(new ResignCommand(UUID.randomUUID(),f.id,"a",a.version()));
        PlayerMatchView terminal=service.getView(f.id,"b");
        assertEquals(MatchPhase.TERMINAL,terminal.phase()); assertEquals(42,terminal.postMatchPieces().size());
        assertEquals(TerminalReason.RESIGNATION,terminal.terminalResult().reason());
        assertEquals(MatchErrorCode.TERMINAL_MATCH,failure(()->service.makeMove(new MakeMoveCommand(UUID.randomUUID(),f.id,"b",terminal.version(),new Position(5,0),new Position(4,0)))).code());
    }

    @Test void moveVersionTurnIdempotencyAndConcurrency() throws Exception {
        Fixture f=active(); PlayerMatchView view=service.getView(f.id,"a"); String mover=view.currentPlayer()==PlayerSide.PLAYER_ONE?"a":"b";
        Position source=view.currentPlayer()==PlayerSide.PLAYER_ONE?new Position(2,0):new Position(5,0);
        Position destination=view.currentPlayer()==PlayerSide.PLAYER_ONE?new Position(3,0):new Position(4,0);
        String other=mover.equals("a")?"b":"a";
        assertEquals(MatchErrorCode.ILLEGAL_MOVE,failure(()->service.makeMove(new MakeMoveCommand(UUID.randomUUID(),f.id,other,view.version(),source,destination))).code());
        assertEquals(view.version(),service.getView(f.id,mover).version());
        assertEquals(MatchErrorCode.STALE_VERSION,failure(()->service.makeMove(new MakeMoveCommand(UUID.randomUUID(),f.id,mover,view.version()-1,source,destination))).code());
        UUID command=UUID.randomUUID(); MakeMoveCommand move=new MakeMoveCommand(command,f.id,mover,view.version(),source,destination);
        MatchCommandResult accepted=service.makeMove(move); assertEquals(view.version()+1,accepted.version());
        assertEquals(accepted,service.makeMove(move));
        assertEquals(MatchErrorCode.COMMAND_CONFLICT,failure(()->service.makeMove(new MakeMoveCommand(command,f.id,mover,view.version(),source,new Position(destination.row(),1)))).code());

        PlayerMatchView next=service.getView(f.id,other); Position s=next.currentPlayer()==PlayerSide.PLAYER_ONE?new Position(2,1):new Position(5,1); Position d=next.currentPlayer()==PlayerSide.PLAYER_ONE?new Position(3,1):new Position(4,1);
        long expected=next.version(); ExecutorService pool=Executors.newFixedThreadPool(2);
        List<Future<Boolean>> futures=List.of(pool.submit(()->accept(new MakeMoveCommand(UUID.randomUUID(),f.id,other,expected,s,d))),pool.submit(()->accept(new MakeMoveCommand(UUID.randomUUID(),f.id,other,expected,s,d))));
        assertEquals(1,futures.stream().filter(x->{try{return x.get();}catch(Exception e){throw new RuntimeException(e);}}).count()); pool.shutdownNow();
    }

    @Test void lockingRetryIsIdempotentAndConflictIsRejected() {
        Fixture f=fixture(); service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(),f.id,"a",2,formation(PlayerSide.PLAYER_ONE)));
        UUID id=UUID.randomUUID(); LockFormationCommand lock=new LockFormationCommand(id,f.id,"a",3);
        MatchCommandResult first=service.lockFormation(lock); assertEquals(first,service.lockFormation(lock));
        assertEquals(MatchErrorCode.COMMAND_CONFLICT,failure(()->service.lockFormation(new LockFormationCommand(id,f.id,"b",3))).code());
    }

    @Test void applicationBattlesUseDomainResolutionAndPublicHistoryHidesRanks() {
        Fixture f=active(); PlayerMatchView view=service.getView(f.id,"a");
        boolean p1First=view.currentPlayer()==PlayerSide.PLAYER_ONE;
        String first=p1First?"a":"b", second=p1First?"b":"a";
        Position firstSource=p1First?new Position(2,0):new Position(5,0), firstMiddle=p1First?new Position(3,0):new Position(4,0);
        MatchCommandResult one=service.makeMove(new MakeMoveCommand(UUID.randomUUID(),f.id,first,view.version(),firstSource,firstMiddle));
        Position secondSource=p1First?new Position(5,0):new Position(2,0), secondMiddle=p1First?new Position(4,0):new Position(3,0);
        MatchCommandResult two=service.makeMove(new MakeMoveCommand(UUID.randomUUID(),f.id,second,one.version(),secondSource,secondMiddle));
        MatchCommandResult battle=service.makeMove(new MakeMoveCommand(UUID.randomUUID(),f.id,first,two.version(),firstMiddle,secondMiddle));
        PlayerMatchView.EventView event=battle.view().events().stream().filter(e->e.type()==PublicMatchEvent.Type.BATTLE_RESOLVED).findFirst().orElseThrow();
        assertEquals(1,event.removedPieceIds().size());
        assertEquals(p1First ? "OWN_PIECE_LOST" : "OWN_PIECE_WON",event.ownBattleOutcome());
        assertFalse(event.toString().contains("PRIVATE"));
    }

    private boolean accept(MakeMoveCommand c){try{service.makeMove(c);return true;}catch(MatchApplicationException e){return false;}}
    private Fixture active(){Fixture f=fixture();long v=submitBoth(f,2);service.lockFormation(new LockFormationCommand(UUID.randomUUID(),f.id,"a",v++));service.lockFormation(new LockFormationCommand(UUID.randomUUID(),f.id,"b",v));return f;}
    private long submitBoth(Fixture f,long v){service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(),f.id,"a",v++,formation(PlayerSide.PLAYER_ONE)));service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(),f.id,"b",v++,formation(PlayerSide.PLAYER_TWO)));return v;}
    private Fixture fixture(){MatchCommandResult c=service.createMatch(new CreateMatchCommand("a"));service.joinMatch(c.view().matchId(),new JoinMatchCommand(UUID.randomUUID(),c.view().roomCode(),"b",1));return new Fixture(c.view().matchId());}
    private record Fixture(UUID id){}
    private static List<FormationPiece> formation(PlayerSide side){
        List<Rank> ranks=new ArrayList<>(List.of(Rank.FIVE_STAR_GENERAL,Rank.FOUR_STAR_GENERAL,Rank.THREE_STAR_GENERAL,Rank.TWO_STAR_GENERAL,Rank.ONE_STAR_GENERAL,Rank.COLONEL,Rank.LIEUTENANT_COLONEL,Rank.MAJOR,Rank.CAPTAIN,Rank.FIRST_LIEUTENANT,Rank.SECOND_LIEUTENANT,Rank.SERGEANT,Rank.SPY,Rank.SPY,Rank.FLAG));
        for(int i=0;i<6;i++)ranks.add(Rank.PRIVATE); List<FormationPiece> pieces=new ArrayList<>(); int start=side==PlayerSide.PLAYER_ONE?0:5;
        for(int i=0;i<ranks.size();i++)pieces.add(new FormationPiece(UUID.randomUUID(),ranks.get(i),new Position(start+i/9,i%9))); return pieces;
    }
    private MatchApplicationException failure(Runnable r){return assertThrows(MatchApplicationException.class,r::run);}
}
