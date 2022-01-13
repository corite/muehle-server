import logic.entities.*;
import logic.exceptions.GameException;
import logic.exceptions.IllegalMoveException;
import logic.exceptions.IllegalPlayerException;
import logic.exceptions.InvalidPhaseException;
import networking.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable{
    private final Socket clientSocket;
    private Game game;
    private Player player;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    public Socket getClientSocket() {
        return clientSocket;
    }

    public Game getGame() {
        if (game != null) {
            return game;
        } else {
            if (getPlayer() != null) {
                synchronized (Main.class) {
                    Player self = getPlayer();
                    ArrayList<Game> games = Main.getGames().stream()
                            .filter(g -> self.equals(g.getPlayer2()) || self.equals(g.getPlayer1()))
                            .collect(Collectors.toCollection(ArrayList::new));
                    if (games.size() == 1) {
                        this.setGame(games.get(0));
                        return games.get(0);
                    } else {
                        logger.error("player {} was present in {} games, expected 1", self.getPlayerId(), games.size());
                        return null;
                    }
                }
            } else return null;
        }
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public void run() {
        try {
            while (true) {
                ObjectInputStream ois = new ObjectInputStream(getClientSocket().getInputStream());

                Object inputObject = ois.readObject();
                if (inputObject instanceof InitialAction) {
                    handleInitialAction((InitialAction) inputObject);
                } else if (inputObject instanceof ListPlayersAction) {
                    handleListPlayersAction((ListPlayersAction) inputObject);
                } else if (inputObject instanceof ConnectAction) {
                    handleConnectAction((ConnectAction) inputObject);
                } else if (inputObject instanceof GameAction) {
                    handleGameAction((GameAction) inputObject);
                } else  if (inputObject instanceof ReconnectAction) {
                    handleReconnectAction((ReconnectAction) inputObject);
                } else  if (inputObject instanceof EndSessionAction) {
                    handleEndSessionAction((EndSessionAction) inputObject);
                } else throw new ClassNotFoundException("Read input object not supported");
            }
        } catch (IOException e) {
            logger.error("the connection was closed",e);
            sendDisconnectResponse();//to the other player who has not been disconnected
        } catch (ClassNotFoundException e) {
            logger.error("(de)serialization failed",e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("failed to close socket",e);
            }
            Thread.currentThread().interrupt();
        }
    }


    private void handleGameAction(GameAction gameAction) {
        logger.debug("handling game action");
        Player self = getPlayerReference(gameAction.getPlayer());

        if (getGame() !=null) {
            synchronized (this.getGame()) {
                String message = "";
                switch (gameAction.getType()) {
                    case PLACE -> {
                        try {
                            getGame().placeStone(self, gameAction.getPlaceOrTakeCoordinate());
                        } catch (GameException e) {
                            message = getGameExceptionMessage(e);
                        }
                    }
                    case MOVE -> {
                        try {
                            getGame().moveStone(self, gameAction.getFrom(), gameAction.getTo());
                        } catch (GameException e) {
                            message = getGameExceptionMessage(e);
                        }
                    }
                    case TAKE -> {
                        try {
                            getGame().takeStone(self, gameAction.getPlaceOrTakeCoordinate());
                        } catch (GameException e) {
                            message = getGameExceptionMessage(e);
                        }
                    }
                }
                sendGameResponseToBothPlayers(message);
            }
        }
    }

    private void handleInitialAction(InitialAction initialAction) throws IOException {
        logger.debug("handling initial action");
        synchronized (Main.class) {
            String name = initialAction.getName();

            int code = Main.getNameCount().getOrDefault(name, 0) +1;//codes will start with value 1
            Main.getNameCount().put(name, code);

            Player self = new Player(name, code, StoneState.NONE ,getClientSocket().getOutputStream());
            Main.getPlayers().add(self);
            this.setPlayer(self);
            InitialResponse initialResponse = new InitialResponse(self);
            sendResponse(self,initialResponse);
        }
    }

    private void handleListPlayersAction(ListPlayersAction listPlayersAction) {
        logger.debug("handling listPlayers action");
        synchronized (Main.class) {
            Player self = getPlayerReference(listPlayersAction.getSelf());
            if (Main.getWaitingPlayers().contains(self)) {
                //should only work if the player itself is also not in a game
                ArrayList<Player> waitingPlayers = new ArrayList<>(Main.getWaitingPlayers());
                waitingPlayers.remove(self); //the asking player should not be included

                ListPlayersResponse listPlayersResponse = new ListPlayersResponse(waitingPlayers);
                sendResponse(self,listPlayersResponse);
            } else {
                logger.warn("could not list players because requesting player {} is already in a game",self.getPlayerId());
            }
        }
    }

    private void handleReconnectAction(ReconnectAction reconnectAction) throws IOException {
        logger.debug("handling reconnect action");
        Player self = getPlayerReference(reconnectAction.getPlayer());

        Game game = findGame(self);
        if (game != null) {
            this.setGame(game);
            synchronized (game) {
                self.setOutputStream(getClientSocket().getOutputStream());
                sendGameResponseToBothPlayers("Player " + self.getPlayerId() + " has reconnected.");
            }
        }

    }

    private void handleEndSessionAction(EndSessionAction endSessionAction) throws IOException{
        logger.debug("handling endSession action");
        if (getGame() != null) {
            synchronized (Main.class) {
                synchronized (getGame()) {
                    Player self = getPlayerReference(endSessionAction.getPlayer());
                    if (self.equals(getGame().getPlayer1()) || self.equals(getGame().getPlayer2())) {

                        EndSessionResponse endSessionResponse = new EndSessionResponse(self.getPlayerId() + " hat das Spiel beendet.");
                        sendResponseToBothPlayers(endSessionResponse);

                        //make players waiting again
                        getGame().getPlayer1().setColor(StoneState.NONE);
                        getGame().getPlayer2().setColor(StoneState.NONE);

                        //make players place again
                        getGame().getPlayer1().setPhase(GamePhase.PLACE);
                        getGame().getPlayer2().setPhase(GamePhase.PLACE);


                        //reset placed stones to 0
                        getGame().getPlayer1().setPlacedStones(0);
                        getGame().getPlayer2().setPlacedStones(0);

                        //remove game from Main datastructures
                        Main.getGames().remove(getGame());
                        //this removes the last local copy of the game Object. This also affects the thread of the other player
                        this.setGame(null);
                    }
                }
            }
        }

    }

    private void handleConnectAction(ConnectAction connectAction) {
        logger.debug("handling connect action");
            synchronized (Main.class) {
                Player self = getPlayerReference(connectAction.getSelf());
                Player other = getPlayerReference(connectAction.getOther());

                if (Main.getRequestedPairs().containsKey(other) && Main.getRequestedPairs().get(other).equals(self)) {
                    //if the other player has already requested a game

                    //figure out player colours
                    double random = Math.random();
                    if (random < 0.5) {
                        self.setColor(StoneState.WHITE);
                        other.setColor(StoneState.BLACK);
                    } else {
                        self.setColor(StoneState.BLACK);
                        other.setColor(StoneState.WHITE);
                    }
                    Game game = new Game(self, other);
                    Main.getGames().add(game);
                    this.setGame(game);
                    String message = game.getNextPlayerToMove().getPlayerId() + " starts!";
                    sendGameResponseToBothPlayers(message);

                    //Main.getRequestedPairs().forEach((p1,p2)->System.out.println(p1.getPlayerId()+" "+p2.getPlayerId()));
                    Main.getRequestedPairs().remove(self);
                    Main.getRequestedPairs().remove(other);
                    //Main.getRequestedPairs().forEach((p1,p2)->System.out.println(p1.getPlayerId()+" "+p2.getPlayerId()));

                } else {
                    Main.getRequestedPairs().put(self, other);
                    //request a game with the player
                }
            }
    }

    private Game findGame(Player player) {
        synchronized (Main.class) {
            for (Game game : Main.getGames()) {
                synchronized (game) {
                    if (player.equals(game.getPlayer1()) || player.equals(game.getPlayer2())) {
                        return game;
                    }
                }
            }
        }
        return null;
    }

    private void sendResponse(Player player, Object response) {
        synchronized (player) {
            try {
                ObjectOutputStream oos = new ObjectOutputStream(player.getOutputStream());
                oos.writeObject(response);
                oos.flush();
                logger.debug("sent response to player {}",player.getPlayerId());
            } catch (IOException e) {
                logger.error("failed sending response to player {}",player.getPlayerId(),e);
            }
        }
    }

    /**
     * this method sends the passed in GameResponse directly to the Player this Thread is assigned to, and sends the same response with isYourTurn negated to the other player
     */
    private void sendResponseToBothPlayers(Object response) {
        sendResponse(getGame().getNextPlayerToMove() ,response);
        sendResponse(getGame().getOtherPlayer(getGame().getNextPlayerToMove()) ,response);
    }
    private void sendGameResponseToBothPlayers(String message) {
        GameResponse gameResponse = new GameResponse(message,getNextAction(),getGame().getNextPlayerToMove(),getGame().getOtherPlayer(getGame().getNextPlayerToMove()),new ArrayList<>(getGame().getField().nodes()));
        sendResponseToBothPlayers(gameResponse);
    }

    private String getGameExceptionMessage(GameException e) {
        if (e instanceof InvalidPhaseException) {
            return "You are not allowed to perform this action in your current Game-Phase";
        } else if (e instanceof IllegalPlayerException) {
            return "Its not your turn to move";
        } else if (e instanceof IllegalMoveException) {
            return  "You are not allowed to move to/from this position";
        } else return "An unknown GameException occurred";
    }
    private ActionType getNextAction() {
        if (getGame().isNextOperationTake()) {
            return ActionType.TAKE;
        } else if (getGame().getNextPlayerToMove().getPhase().equals(GamePhase.PLACE)) {
            return ActionType.PLACE;
        } else return ActionType.MOVE;
    }

    /**
     * should be used to get the locally stored Player Object from the passed in Player Object
     */
    private Player getPlayerReference(Player player) {
        synchronized (Main.class) {
            return Main.getPlayers().stream().filter(p -> {
                synchronized (p) {
                    return p.equals(player);
                }
            }).findFirst().orElseThrow(IllegalPlayerException::new);
        }
    }

    private void sendDisconnectResponse() {
        logger.debug("handling disconnect response");
        if (getGame()!= null) {
            synchronized (getGame()) {
                Player player = getGame().getOtherPlayer(getPlayer());
                sendResponse(player, new DisconnectResponse(player));
            }
        }
    }
}
