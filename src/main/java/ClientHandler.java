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
    private Player player;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    public Socket getClientSocket() {
        return clientSocket;
    }

    public Game getGame() {
        if (getPlayer() != null) {
            synchronized (Main.class) {
                Player self = getPlayer();
                ArrayList<Game> games = Main.getGames().stream()
                        .filter(g -> {
                            synchronized (g) {
                                return self.equals(g.getPlayer2()) || self.equals(g.getPlayer1());
                            }
                        })
                        .collect(Collectors.toCollection(ArrayList::new));
                if (games.size() == 1) {
                    return games.get(0);
                } else if (games.size() == 0){
                    logger.debug("requested player {} was present in 0 games", self.getPlayerId());
                    return null;
                } else {
                    logger.warn("player {} was present in {} games, expected 1 or 0",self.getPlayerId(),games.size());
                    return null;
                }
            }
        } else return null;

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
        Game game = getGame();
        Player self = getPlayerReference(gameAction.getPlayer(),game);

        if (game !=null) {
            synchronized (game) {
                String message = "";
                switch (gameAction.getType()) {
                    case PLACE -> {
                        try {
                            game.placeStone(self, gameAction.getPlaceOrTakeCoordinate());
                        } catch (GameException e) {
                            message = getGameExceptionMessage(e);
                        }
                    }
                    case MOVE -> {
                        try {
                            game.moveStone(self, gameAction.getFrom(), gameAction.getTo());
                        } catch (GameException e) {
                            message = getGameExceptionMessage(e);
                        }
                    }
                    case TAKE -> {
                        try {
                            game.takeStone(self, gameAction.getPlaceOrTakeCoordinate());
                        } catch (GameException e) {
                            message = getGameExceptionMessage(e);
                        }
                    }
                }
                sendGameResponseToBothPlayers(message,game);
            }
        }
    }

    private void handleInitialAction(InitialAction initialAction) throws IOException {
        logger.debug("handling initial action");
        if (getGame() == null) {
            synchronized (Main.class) {
                String name = initialAction.getName();

                int code = Main.getNameCount().getOrDefault(name, 0) + 1;//codes will start with value 1
                Main.getNameCount().put(name, code);

                Player self = new Player(name, code, StoneState.NONE, getClientSocket().getOutputStream());
                Main.getWaitingPlayers().add(self);
                this.setPlayer(self);
                InitialResponse initialResponse = new InitialResponse(self);
                sendResponse(self, initialResponse);
            }
        }
    }

    private void handleListPlayersAction(ListPlayersAction listPlayersAction) {
        logger.debug("handling listPlayers action");
        if (getGame() == null) {
            synchronized (Main.class) {
                Player self = getPlayerReference(listPlayersAction.getSelf(),null);
                if (Main.getWaitingPlayers().contains(self)) {
                    //should only work if the player itself is also not in a game
                    ArrayList<Player> waitingPlayers = new ArrayList<>(Main.getWaitingPlayers());
                    waitingPlayers.remove(self); //the asking player should not be included

                    ListPlayersResponse listPlayersResponse = new ListPlayersResponse(waitingPlayers);
                    sendResponse(self, listPlayersResponse);
                } else {
                    logger.warn("could not list players because requesting player {} is already in a game", self.getPlayerId());
                }
            }
        }
    }

    private void handleReconnectAction(ReconnectAction reconnectAction) throws IOException {
        logger.debug("handling reconnect action");
        Game game = getGame();
        Player self = getPlayerReference(reconnectAction.getPlayer(), game);

        if (game != null) {
            synchronized (game) {
                self.setOutputStream(getClientSocket().getOutputStream());
                sendGameResponseToBothPlayers("Player " + self.getPlayerId() + " has reconnected.", game);
            }
        }

    }

    private void handleEndSessionAction(EndSessionAction endSessionAction) throws IOException{
        logger.debug("handling endSession action");
        Game game = getGame();
        if (game != null) {
            synchronized (Main.class) {
                synchronized (game) {
                    Player self = getPlayerReference(endSessionAction.getPlayer(),game);
                    if (self.equals(game.getPlayer1()) || self.equals(game.getPlayer2())) {

                        EndSessionResponse endSessionResponse = new EndSessionResponse(self.getPlayerId() + " hat das Spiel beendet.");
                        sendResponseToBothPlayers(endSessionResponse, game);

                        //make players waiting again
                        game.getPlayer1().setColor(StoneState.NONE);
                        game.getPlayer2().setColor(StoneState.NONE);

                        //make players place again
                        game.getPlayer1().setPhase(GamePhase.PLACE);
                        game.getPlayer2().setPhase(GamePhase.PLACE);

                        //reset placed stones to 0
                        game.getPlayer1().setPlacedStones(0);
                        game.getPlayer2().setPlacedStones(0);

                        //add players to waiting players again
                        Main.getWaitingPlayers().add(game.getPlayer1());
                        Main.getWaitingPlayers().add(game.getPlayer2());

                        //remove game from Main datastructures
                        Main.getGames().remove(game);

                    }
                }
            }
        }

    }

    private void handleConnectAction(ConnectAction connectAction) {
        logger.debug("handling connect action");
        if (getGame() == null) {
            synchronized (Main.class) {
                Player self = getPlayerReference(connectAction.getSelf(),null);
                Player other = getPlayerReference(connectAction.getOther(),null);

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

                    //removing players from waiting status
                    Main.getWaitingPlayers().remove(self);
                    Main.getWaitingPlayers().remove(other);

                    //remove from requested pairs, since they are now in a game
                    Main.getRequestedPairs().remove(self);
                    Main.getRequestedPairs().remove(other);


                    String message = game.getNextPlayerToMove().getPlayerId() + " starts!";
                    synchronized (game) {
                        sendGameResponseToBothPlayers(message, game);
                    }
                    //Main.getRequestedPairs().forEach((p1,p2)->System.out.println(p1.getPlayerId()+" "+p2.getPlayerId()));
                    //Main.getRequestedPairs().forEach((p1,p2)->System.out.println(p1.getPlayerId()+" "+p2.getPlayerId()));

                } else {
                    Main.getRequestedPairs().put(self, other);
                    //request a game with the player
                }
            }
        }
    }

    private void sendResponse(Player player, Object response) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(player.getOutputStream());
            oos.writeObject(response);
            oos.flush();
            logger.debug("sent response to player {}", player.getPlayerId());
        } catch (IOException e) {
            logger.error("failed sending response to player {}", player.getPlayerId(), e);
        }

    }

    /**
     * this method sends the passed in GameResponse directly to the Player this Thread is assigned to, and sends the same response with isYourTurn negated to the other player
     */
    private void sendResponseToBothPlayers(Object response, Game game) {
        sendResponse(game.getNextPlayerToMove(), response);
        sendResponse(game.getOtherPlayer(game.getNextPlayerToMove()), response);
    }
    private void sendGameResponseToBothPlayers(String message, Game game) {
        GameResponse gameResponse = new GameResponse(message, getNextAction(game), game.getNextPlayerToMove(), game.getOtherPlayer(game.getNextPlayerToMove()), new ArrayList<>(game.getField().nodes()));
        sendResponseToBothPlayers(gameResponse, game);
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
    private ActionType getNextAction(Game game) {
        if (game != null) {
            if (game.isNextOperationTake()) {
                return ActionType.TAKE;
            } else if (game.getNextPlayerToMove().getPhase().equals(GamePhase.PLACE)) {
                return ActionType.PLACE;
            } else return ActionType.MOVE;
        } else throw new InvalidPhaseException();
    }

    /**
     * should be used to get the locally stored Player Object from the passed in Player Object
     */
    private Player getPlayerReference(Player player, Game game) {
        if (game != null) {
            synchronized (game) {
                if (game.getPlayer1().equals(player)) {
                    return game.getPlayer1();
                } else if (game.getPlayer2().equals(player)) {
                    return game.getPlayer2();
                } else throw new IllegalPlayerException();
            }
        } else {
            synchronized (Main.class) {
                return Main.getWaitingPlayers().stream().filter(p -> p.equals(player)).findFirst().orElseThrow(IllegalPlayerException::new);
            }
        }
    }

    private void sendDisconnectResponse() {
        logger.debug("handling disconnect response");
        Game game = getGame();
        if (game!= null) {
            synchronized (game) {
                Player player = game.getOtherPlayer(getPlayer());
                sendResponse(player, new DisconnectResponse(player));
            }
        }
    }

}
