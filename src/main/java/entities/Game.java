package backend.entities;

import backend.exceptions.GameException;
import backend.exceptions.IllegalMoveException;
import backend.exceptions.InvalidPhaseException;
import backend.exceptions.IllegalPlayerException;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

import static backend.entities.GamePhase.*;
import static backend.entities.StoneState.NONE;

public class Game {
    private final ImmutableGraph<Position> field;
    private final Player player1;
    private final Player player2;
    private Player nextPlayerToMove;
    private boolean isNextOperationTake;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public Game(Player player1, Player player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.field = getInitialField();
        this.nextPlayerToMove = getStartPlayer();
    }

    public ImmutableGraph<Position> getField() {
        return field;
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public Player getNextPlayerToMove() {
        return nextPlayerToMove;
    }

    private void setNextPlayerToMove(Player nextPlayerToMove) {
        this.nextPlayerToMove = nextPlayerToMove;
    }

    public boolean isNextOperationTake() {
        return isNextOperationTake;
    }

    private void setNextOperationTake(boolean nextOperationTake) {
        isNextOperationTake = nextOperationTake;
    }

    private Player getStartPlayer() {
        if (getPlayer1() == null || getPlayer2() == null) {
            throw new IllegalArgumentException("Player cannot be null");
        } else if (getPlayer1().getColor().equals(getPlayer2().getColor())) {
            throw new IllegalArgumentException("Players have to have different colors");
        } //checking that parameters are valid

        return getPlayer1().getColor().equals(StoneState.WHITE) ? getPlayer1() : getPlayer2(); // white player begins the game
    }

    private ImmutableGraph<Position> getInitialField() {
        Position p1 = new Position(-3,3);
        Position p2 = new Position(0,3);
        Position p3 = new Position(3,3);

        Position p4 = new Position(-2,2);
        Position p5 = new Position(0,2);
        Position p6 = new Position(2,2);

        Position p7 = new Position(-1,1);
        Position p8 = new Position(0,1);
        Position p9 = new Position(1,1);

        Position p10 = new Position(-3,0);
        Position p11 = new Position(-2,0);
        Position p12 = new Position(-1,0);
        Position p13 = new Position(1,0);
        Position p14 = new Position(2,0);
        Position p15 = new Position(3,0);

        Position p16 = new Position(-1,-1);
        Position p17 = new Position(0,-1);
        Position p18 = new Position(1,-1);

        Position p19 = new Position(-2,-2);
        Position p20 = new Position(0,-2);
        Position p21 = new Position(2,-2);

        Position p22 = new Position(-3,-3);
        Position p23 = new Position(0,-3);
        Position p24 = new Position(3,-3);

        return GraphBuilder
                .undirected()
                .<Position>immutable()

                .putEdge(p1,p2)
                .putEdge(p2,p3)
                .putEdge(p1,p10)
                .putEdge(p2,p5)
                .putEdge(p3,p15)

                .putEdge(p4,p5)
                .putEdge(p5,p6)
                .putEdge(p4,p11)
                .putEdge(p5,p8)
                .putEdge(p6,p14)

                .putEdge(p7,p8)
                .putEdge(p8,p9)
                .putEdge(p7,p12)
                .putEdge(p9,p13)


                .putEdge(p10,p11)
                .putEdge(p11,p12)
                .putEdge(p13,p14)
                .putEdge(p14,p15)


                .putEdge(p16,p17)
                .putEdge(p17,p18)
                .putEdge(p16,p12)
                .putEdge(p18,p13)

                .putEdge(p19,p20)
                .putEdge(p20,p21)
                .putEdge(p19,p11)
                .putEdge(p20,p17)
                .putEdge(p21,p14)

                .putEdge(p22,p23)
                .putEdge(p23,p24)
                .putEdge(p22,p10)
                .putEdge(p23,p20)
                .putEdge(p24,p15)
                .build();
    }

    /**
     * Checks if it is this players turn to make a move.
     * @param player the player to be checked
     */
    private boolean isThisPlayersTurn(Player player) {
        return this.getNextPlayerToMove().equals(player);
    }

    public Position getPositionAtCoordinate(Coordinate coordinate) {
        return getField()
                .nodes()
                .stream()
                .filter(position -> position.getCoordinate().equals(coordinate))
                .findFirst()
                .get();
    }
    private boolean isCoordinateOccupied(Coordinate coordinate) {
        return !NONE.equals(getPositionAtCoordinate(coordinate).getStoneState());
    }


    /**
     * checks whether the player has a stone at position 'from' and whether position 'to' is not occupied
     */
    private boolean isMovePossible(Player player, Coordinate from, Coordinate to) {
        return getPositionAtCoordinate(from).getStoneState().equals(player.getColor()) && getPositionAtCoordinate(to).getStoneState().equals(NONE);
    }
    private boolean areAdjacentNodes(Coordinate from, Coordinate to) {
        return getField().adjacentNodes(getPositionAtCoordinate(from)).contains(getPositionAtCoordinate(to));
    }




    private ArrayList<Position> getPositions(Player player) {
        return getField()
                .nodes()
                .stream()
                .filter(position -> position.getStoneState().equals(player.getColor()))
                .collect(Collectors.toCollection(ArrayList::new));
    }



    private Player getOtherPlayer(Player player) {
        if (getPlayer1().equals(player)) {
            return getPlayer2();
        } else if (getPlayer2().equals(player)) {
            return getPlayer1();
        } else {
            throw new IllegalPlayerException();
        }
    }
    private void updateGamePhases() {
        updateGamePhase(getPlayer1());
        updateGamePhase(getPlayer2());
    }

    private void updateGamePhase(Player player) {
        GamePhase oldPhase = player.getPhase();
        switch (oldPhase) {
            case PLACE -> {
                if (player.getPlacedStones()==9) {
                    player.setPhase(MOVE);
                    logger.info("player {} is now in phase MOVE",player.getName());
                }
            }
            case MOVE -> {
                if (getPositions(player).size()<=3) {
                    player.setPhase(FLY);
                    logger.info("player {} is now in phase FLY",player.getName());
                }
                if (!isAbleToMove(player)) {
                    player.setPhase(LOST);
                    logger.info("player {} is now in phase LOST",player.getName());
                    getOtherPlayer(player).setPhase(WON);
                    logger.info("player {} is now in phase WON",getOtherPlayer(player).getName());
                }
            }
            case FLY -> {
                if (getPositions(player).size()<=2) {
                    player.setPhase(LOST);
                    logger.info("player {} is now in phase LOST",player.getName());
                    getOtherPlayer(player).setPhase(WON);
                    logger.info("player {} is now in phase WON",getOtherPlayer(player).getName());
                }
            }
        }
    }


    private boolean isPartOfMill(Coordinate coordinate) {
        return isPartOfHorizontalMill(coordinate) || isPartOfVerticalMill(coordinate);
    }
    private boolean isPartOfHorizontalMill(Coordinate coordinate) {
        int x = coordinate.getX();
        int y = coordinate.getY();
        StoneState stoneState = getPositionAtCoordinate(coordinate).getStoneState();

        ArrayList<Coordinate> row = getField().nodes().stream()
                .filter(p -> p.getCoordinate().getY() == y)
                .filter(p -> p.getStoneState().equals(stoneState))
                .map(Position::getCoordinate)
                .collect(Collectors.toCollection(ArrayList::new));

        if (y!= 0) {
            //then there are only 3 stones on each line, if there is a mill they all have to have the same stoneState
            return row.size() == 3;
        } else if (x<0) {
            // if y==0 , the mill can either be on the right of the center or on the left
            return row.stream().filter(c -> c.getX() < 0).count() == 3;
        } else {
            return row.stream().filter(c -> c.getX() > 0).count() == 3;
        }
    }

    private boolean isPartOfVerticalMill( Coordinate coordinate) {
        int x = coordinate.getX();
        int y = coordinate.getY();
        StoneState stoneState = getPositionAtCoordinate(coordinate).getStoneState();

        ArrayList<Coordinate> row = getField().nodes().stream()
                .filter(p -> p.getCoordinate().getX() == x)
                .filter(p -> p.getStoneState().equals(stoneState))
                .map(Position::getCoordinate)
                .collect(Collectors.toCollection(ArrayList::new));

        if (x!= 0) {
            //then there are only 3 stones on each line, if there is a mill they all have to have the same stoneState
            return row.size() == 3;
        } else if (y<0) {
            // if x==0 , the mill can either be above or under the center
            return row.stream().filter(c -> c.getY() < 0).count() == 3;
        } else {
            return row.stream().filter(c -> c.getY() > 0).count() == 3;
        }
    }

    private boolean isAbleToMove(Player player) {
        ArrayList<Position> playerPositions = getPositions(player);
        for (Position position : playerPositions) {
            if (getField().adjacentNodes(position).stream().anyMatch(pos -> NONE.equals(pos.getStoneState()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAllStonesInMills(Player player) {
        for(Position position : getPositions(player)) {
            if (!isPartOfMill(position.getCoordinate())) {
                return false;
            }
        }
        return true;
    }

    private void checkValidPlaceInput(Player player, Coordinate coordinate) throws GameException{
        if (!isThisPlayersTurn(player)) {
            throw new IllegalPlayerException();
        } else if (!GamePhase.PLACE.equals(player.getPhase())) {
            throw new InvalidPhaseException();
        }

        if (isCoordinateOccupied(coordinate)) {
            throw new IllegalMoveException();
        }
    }

    private void checkValidMoveInput(Player player, Coordinate from, Coordinate to) throws GameException{
        if (!isThisPlayersTurn(player)) {
            throw new IllegalPlayerException();
        }

        switch (player.getPhase()) {
            case MOVE -> {
                if (!isMovePossible(player,from,to) || !areAdjacentNodes(from,to)) {
                    //Checks the StoneStates of from and to
                    throw new IllegalMoveException();
                }
            }
            case FLY -> {
                if (!isMovePossible(player,from,to)) {
                    throw new IllegalMoveException();
                }
            }
            default -> throw new InvalidPhaseException();
        }
    }

    private void checkValidTakeInput(Player player, Coordinate coordinate) throws GameException{
        if (!isThisPlayersTurn(player)) {
            throw new IllegalPlayerException();
        }
        if (!isNextOperationTake()) {
            throw new IllegalMoveException();
        }
        if (!getOtherPlayer(player).getColor().equals(getPositionAtCoordinate(coordinate).getStoneState())) {
            //a player can only take stones from another player, not from himself or unoccupied stones
            throw new IllegalMoveException();
        }
        if (isPartOfMill(coordinate) && !hasAllStonesInMills(getOtherPlayer(player))) {
            //stone to take can not be in a mill, except the other player only has stones in mills left
            throw  new IllegalMoveException();
        }
    }

    /**
     * use this method only in the players 'place' phase in order to place a stone on the field
     * @param player that wants to place the stone
     * @param coordinate where the stone is supposed to be placed
     * @throws GameException if the operation was illegal.
     */
    public void placeStone(Player player, Coordinate coordinate) throws GameException {
        checkValidPlaceInput(player,coordinate);
        getPositionAtCoordinate(coordinate).setStoneState(player.getColor());
        player.addPlacedStone();
        updateGamePhases();

        if (isPartOfMill(coordinate)) {
            setNextPlayerToMove(player);
            setNextOperationTake(true);
        } else {
            setNextPlayerToMove(getOtherPlayer(player));
        }
        logger.debug("player {} successfully placed stone at coordinate ({},{})",player.getName(),coordinate.getX(),coordinate.getY());
    }

    /**
     * use this method in the move and the fly phase in order to move stones
     * @param player that wants to place the stone
     * @param from the position the player wants to move
     * @param to the position the player wants to move to
     * @throws GameException if the operation was somehow illegal
     */
    public void moveStone(Player player, Coordinate from, Coordinate to) throws GameException {
        checkValidMoveInput(player,from,to);
        getPositionAtCoordinate(from).setStoneState(NONE);
        getPositionAtCoordinate(to).setStoneState(player.getColor());
        updateGamePhases();

        if (isPartOfMill(to)) {
            setNextPlayerToMove(player);
            setNextOperationTake(true);
        } else {
            setNextPlayerToMove(getOtherPlayer(player));
        }
        logger.debug("player {} successfully moved stone from ({},{}) to ({},{})",player.getName(),from.getX(),from.getY(),to.getX(),to.getY());
    }

    /**
     * use this method after the player has completed a mill and can take a stone from the other player
     * @param player player that wants to take the stone
     * @param stoneToTake the stone he wants to take
     * @throws GameException if the operation was somehow illegal
     */
    public void takeStone(Player player, Coordinate stoneToTake) throws GameException {
        checkValidTakeInput(player,stoneToTake);
        getPositionAtCoordinate(stoneToTake).setStoneState(NONE);
        updateGamePhases();

        setNextOperationTake(false);// will continue as per usual in the next move
        setNextPlayerToMove(getOtherPlayer(player));
        logger.debug("player {} successfully took stone at coordinate ({},{})",player.getName(),stoneToTake.getX(),stoneToTake.getY());
    }

    public void printField() {
        for (int i = 3; i >= -3; i--) {
            int finalI = i;
            getField().nodes().stream()
                    .filter(pos -> pos.getCoordinate().getY() == finalI)
                    .sorted(Comparator.comparingInt(pos -> pos.getCoordinate().getX()))
                    .map(Position::getStoneState)
                    .forEach(System.out::print);
            System.out.println();
        }
    }

}
