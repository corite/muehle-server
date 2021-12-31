import logic.entities.Game;
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
    Socket clientSocket;
    Game game;
    Logger logger = LoggerFactory.getLogger(getClass());
    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    public Socket getClientSocket() {
        return clientSocket;
    }

    public void setClientSocket(Socket clientSocket) {
        this.clientSocket = clientSocket;
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
                //todo: implement connect action
                if (inputObject instanceof InitialAction) {
                    handleInitialAction((InitialAction) inputObject);
                } else if (inputObject instanceof ListPlayersAction) {
                    handleListPlayersAction((ListPlayersAction) inputObject);
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
        Player self = gameAction.getPlayer();

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
            switch (gameAction.getType()) {
                case PLACE -> {
                    String message = "";
                    try {
                        getGame().placeStone(self,gameAction.getPlaceOrTakeCoordinate());

                    } catch (GameException e) {
                        message = handleGameException(e);
                    } finally {
                        GameResponse gameResponse = new GameResponse(message,getGame().getNextPlayerToMove().equals(self),getNextAction(),new ArrayList<>(getGame().getField().nodes()));
                    }
                }
                case MOVE -> {
                    String message = "";
                    try {
                        getGame().moveStone(self,gameAction.getFrom(),gameAction.getTo());
                    } catch (GameException e) {
                        message = handleGameException(e);
                    } finally {
                        GameResponse gameResponse = new GameResponse(message,getGame().getNextPlayerToMove().equals(self),getNextAction(),new ArrayList<>(getGame().getField().nodes()));
                    }
                }
                case TAKE -> {
                    String message = "";
                    try {
                        getGame().takeStone(self,gameAction.getPlaceOrTakeCoordinate());
                    } catch (GameException e) {
                        message = handleGameException(e);
                    } finally {
                        GameResponse gameResponse = new GameResponse(message,getGame().getNextPlayerToMove().equals(self),getNextAction(),new ArrayList<>(getGame().getField().nodes()));
                    }
                }
            }
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
            Player self = listPlayersAction.getSelf();
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

    private void sendResponse(Player player, Object response) {
        //todo: implement sending a response also to the other player automatically
        synchronized (player) {
            try {
                ObjectOutputStream oos = new ObjectOutputStream(player.getOutputStream());
                oos.writeObject(response);
                logger.debug("sent response to player {}",player.getPlayerId());
            } catch (IOException e) {
                logger.error("failed sending response to player {}",player.getPlayerId(),e);
            }
        }
    }

    private String handleGameException(GameException e) {
        //todo: use more specific Exceptions in order to give helpful feedback
        if (e instanceof InvalidPhaseException) {
            return "You are not allowed to perform this action in your current Game-Phase";
        } else if (e instanceof IllegalPlayerException) {
            return "Its not your turn to move";
        } else if (e instanceof IllegalMoveException) {
            return  "You are not allowed to move to/from this position";
        } else return "Internal Server Error";
    }
    private ActionType getNextAction() {
        //todo: figure out next action from game information
        return null;
    }
}
