import logic.entities.Game;
import logic.entities.GamePhase;
import logic.entities.Player;
import logic.entities.StoneState;
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
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    public Socket getClientSocket() {
        return clientSocket;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
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
                } else throw new ClassNotFoundException("Read input object not supported");
            }
        } catch (IOException e) {
            logger.error("the connection was closed",e);
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

        if (getGame() ==null ) {//game is not initialized, should only happen once
            synchronized (Main.class) {
                ArrayList<Game> games = Main.getGames().stream()
                        .filter(g -> self.equals(g.getPlayer2()) || self.equals(g.getPlayer1()))
                        .collect(Collectors.toCollection(ArrayList::new));
                if (games.size() == 1) {
                    this.setGame(games.get(0));
                } else {
                    logger.error("player {} was present in {} games, expected 1",self.getPlayerId(),games.size());
                    return;
                }
            }
        }
        synchronized (this.getGame()) {
            String message = "";
            switch (gameAction.getType()) {
                case PLACE -> {
                    try {
                        getGame().placeStone(self,gameAction.getPlaceOrTakeCoordinate());
                    } catch (GameException e) {
                        message = getGameExceptionMessage(e);
                    }
                }
                case MOVE -> {
                    try {
                        getGame().moveStone(self,gameAction.getFrom(),gameAction.getTo());
                    } catch (GameException e) {
                        message = getGameExceptionMessage(e);
                    }
                }
                case TAKE -> {
                    try {
                        getGame().takeStone(self,gameAction.getPlaceOrTakeCoordinate());
                    } catch (GameException e) {
                        message = getGameExceptionMessage(e);
                    }
                }
            }
            GameResponse gameResponse = new GameResponse(message,getGame().getNextPlayerToMove().equals(self),getNextAction(),new ArrayList<>(getGame().getField().nodes()));
            sendGameResponseToBothPlayers(gameResponse,self);
        }
    }

    private void handleInitialAction(InitialAction initialAction) throws IOException {
        synchronized (Main.class) {
            logger.debug("handling initial action");
            String name = initialAction.getName();

            int code = Main.getNameCount().getOrDefault(name, 0) +1;//codes will start with value 1
            Main.getNameCount().put(name, code);

            Player self = new Player(name, code, StoneState.NONE ,getClientSocket().getOutputStream());
            Main.getPlayers().add(self);
            InitialResponse initialResponse = new InitialResponse(self);
            sendResponse(self,initialResponse);
        }
    }

    private void handleListPlayersAction(ListPlayersAction listPlayersAction) {
        synchronized (Main.class) {
            logger.debug("handling listPlayers action");
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

    private void handleReconnectAction(ReconnectAction reconnectAction) {
        logger.info("recon");
    }

    private void handleConnectAction(ConnectAction connectAction) {
        synchronized (Main.class) {
            Player self = getPlayerReference(connectAction.getSelf());
            Player other = getPlayerReference(connectAction.getOther());

            //figure out player colours
            double random = Math.random();
            if (random < 0.5){
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
            GameResponse response = new GameResponse(message,game.getNextPlayerToMove().equals(self),getNextAction(),new ArrayList<>(game.getField().nodes()));
            sendGameResponseToBothPlayers(response,self);

        }
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
    private void sendGameResponseToBothPlayers(GameResponse response, Player self) {
        sendResponse(self,response);

        Player otherPlayer = getGame().getOtherPlayer(self);
        GameResponse responseToOtherPlayer = new GameResponse(response.getMessage(), !response.isYourTurn(),response.getNextAction(),response.getGameField());

        sendResponse(otherPlayer,responseToOtherPlayer);

    }

    private String getGameExceptionMessage(GameException e) {
        //todo: use more specific Exceptions in order to give helpful feedback
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
            return Main.getPlayers().stream().filter(p -> p.equals(player)).findFirst().orElseThrow(IllegalPlayerException::new);
        }
    }
}
