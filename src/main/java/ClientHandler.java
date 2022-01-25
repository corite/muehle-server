import logic.entities.*;
import logic.exceptions.*;
import networking.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class ClientHandler implements Runnable{
    private final Socket clientSocket;
    private User user;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Socket getClientSocket() {
        return clientSocket;
    }


    public Game getGame(User user) {
        if (user != null) {
            synchronized (Main.class) {
                ArrayList<Game> games = Main.getGames().stream()
                        .filter(g -> {
                            synchronized (g) {
                                return user.equals(g.getPlayer2().getUser()) || user.equals(g.getPlayer1().getUser());
                            }
                        })
                        .collect(Collectors.toCollection(ArrayList::new));
                if (games.size() == 1) {
                    //player is only in one game, everything is fine
                    return games.get(0);
                } else if (games.size() == 0){
                    logger.debug("requested player {} was present in 0 games", user.getName());
                    return null;
                } else {
                    logger.warn("player {} was present in {} games, expected 1 or 0", user.getName() ,games.size());
                    return null;
                }
            }
        } else return null;
    }
    private Game getGame() {
        if (this.getUser() != null) {
            return getGame(this.getUser());
        } else return null;
    }

    private Player getPlayer() {
        return getPlayer(this.getUser());
    }
    private Player getPlayer(User user) {
        Game game = getGame(user);
        if (game != null) {
            synchronized (game) {
                if (game.getPlayer1().getUser().equals(user)) {
                    return game.getPlayer1();
                } else return game.getPlayer2();
            }
        } else return null;
    }


    @Override
    public void run() {
        try {
            while (true) {
                ObjectInputStream ois = new ObjectInputStream(getClientSocket().getInputStream());

                Object inputObject = ois.readObject();
                if (inputObject instanceof RegisterLoginUserAction) {
                    handleRegisterLoginUserAction((RegisterLoginUserAction) inputObject);
                } else if (inputObject instanceof ListUsersAction) {
                    handleListUsersAction((ListUsersAction) inputObject);
                } else if (inputObject instanceof ConnectAction) {
                    handleConnectAction((ConnectAction) inputObject);
                } else if (inputObject instanceof GameAction) {
                    handleGameAction((GameAction) inputObject);
                } else  if (inputObject instanceof ReconnectAction) {
                    handleReconnectAction((ReconnectAction) inputObject);
                } else  if (inputObject instanceof EndGameAction) {
                    handleEndGameAction((EndGameAction) inputObject);
                } else  if (inputObject instanceof EndSessionAction) {
                    handleEndSessionAction((EndSessionAction) inputObject);
                } else throw new ClassNotFoundException("Read input object not supported");
            }
        } catch (IOException e) {
            logger.error("the connection was closed",e);
        } catch (ClassNotFoundException e) {
            logger.error("(de)serialization failed",e);
        } finally {
            Game game = getGame();
            if (game != null) {
                sendDisconnectResponse(game);//to the other player who has not been disconnected
            }
            logOff(getUser());



            try {
                getClientSocket().close();
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

    private void handleRegisterLoginUserAction(RegisterLoginUserAction registerLoginUserAction) throws IOException {
        logger.debug("handling registerLoginUser action");
        if (getGame() == null) {
            synchronized (Main.class) {
                String name = registerLoginUserAction.getName();
                String password = registerLoginUserAction.getPassword();
                User user = new User(name, getClientSocket().getOutputStream());
                String failMessage = "";
                try {
                    if (registerLoginUserAction.isRegisterAction()) {
                        failMessage = "Registrierung fehlgeschlagen, dieser Name ist bereits vergeben.";
                        Main.getDatabaseHandler().createUser(name, password);
                    }
                    failMessage = "Login fehlgeschlagen, das Passwort ist falsch oder der Nutzer ist bereits eingeloggt.";
                    Main.getDatabaseHandler().acquireUserLock(name, password);

                    this.setUser(user);
                    Main.getWaitingUsers().add(user);

                    RegisterLoginUserResponse response = new RegisterLoginUserResponse(user, true, "");
                    sendResponse(user, response);

                } catch (SQLException e) {
                    logger.error("failed to log in user {}", name, e);
                    RegisterLoginUserResponse response = new RegisterLoginUserResponse(user, false, failMessage);
                    sendResponse(user,response);
                }

            }
        }
    }

    private void handleListUsersAction(ListUsersAction listUsersAction) {
        logger.debug("handling listUsers action");
        if (getGame() == null) {
            synchronized (Main.class) {
                User self = getUserReference(listUsersAction.getSelf());
                if (Main.getWaitingUsers().contains(self)) {
                    //should only work if the player itself is also not in a game
                    ArrayList<User> waitingUsers = new ArrayList<>(Main.getWaitingUsers());
                    waitingUsers.remove(self); //the asking player should not be included

                    ListUsersResponse listPlayersResponse = new ListUsersResponse(waitingUsers);
                    sendResponse(self, listPlayersResponse);
                } else {
                    logger.warn("could not list players because requesting player {} is already in a game", self.getName());
                }
            }
        }
    }

    private void handleReconnectAction(ReconnectAction reconnectAction) throws IOException {
        logger.debug("handling reconnect action");
        Player self = reconnectAction.getPlayer();
        Game game = getGame(self.getUser());

        if (game != null) {
            synchronized (game) {
                self.setOutputStream(getClientSocket().getOutputStream());
                this.setUser(self.getUser());
                sendGameResponseToBothPlayers("Player " + self.getName() + " has reconnected.", game);
            }
        }

    }

    private void handleEndSessionAction(EndSessionAction endSessionAction) throws IOException{
        logger.debug("handling endSession action");
        User user = getUserReference(endSessionAction.getUser());
        if (!isUserInGame(user)) {
            logOff(user);
        } else logger.warn("user is still in a game, send EndGameAction first");
    }

    private void logOff(User self) {

        synchronized (Main.class) {
            Main.getRequestedPairs().remove(self);
            Main.getWaitingUsers().remove(self);


            try {
                Main.getDatabaseHandler().releaseUserLock(self.getName());
                getClientSocket().close();
            } catch (SQLException e) {
                logger.error("failed to release lock on user {}", self.getName(), e);
            } catch (IOException e) {
                logger.error("failed to close socket",e);
            }
            Thread.currentThread().interrupt();
        }

    }

    private void handleConnectAction(ConnectAction connectAction) {
        logger.debug("handling connect action");
        if (getGame() == null) {
            synchronized (Main.class) {
                User selfUser = getUserReference(connectAction.getSelf());
                User otherUser = getUserReference(connectAction.getOther());

                if (Main.getRequestedPairs().containsKey(otherUser) && Main.getRequestedPairs().get(otherUser).equals(selfUser)) {
                    //if the other player has already requested a game
                    Player selfPlayer;
                    Player otherPlayer;

                    //figure out player colours
                    double random = Math.random();
                    if (random < 0.5) {
                        selfPlayer = new Player(selfUser, StoneState.WHITE);
                        otherPlayer = new Player(otherUser, StoneState.BLACK);
                    } else {
                        selfPlayer = new Player(selfUser, StoneState.BLACK);
                        otherPlayer = new Player(otherUser, StoneState.WHITE);
                    }
                    Game game = new Game(selfPlayer, otherPlayer);
                    Main.getGames().add(game);

                    //removing players from waiting status
                    Main.getWaitingUsers().remove(selfUser);
                    Main.getWaitingUsers().remove(otherUser);

                    //remove from requested pairs, since they are now in a game
                    Main.getRequestedPairs().remove(selfUser);
                    Main.getRequestedPairs().remove(otherUser);

                    String message = game.getNextPlayerToMove().getName() + " beginnt!";
                    synchronized (game) {
                        sendGameResponseToBothPlayers(message, game);
                    }

                } else {
                    Main.getRequestedPairs().put(selfUser, otherUser);
                    //request a game with the player
                }
            }
        }
    }

    private void sendResponse(User user, Object response) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(user.getOutputStream());
            oos.writeObject(response);
            oos.flush();
            logger.debug("sent response to player {}", user.getName());
        } catch (IOException e) {
            logger.error("failed sending response to player {}", user.getName(), e);
        }

    }


    /**
     * this method sends the passed in GameResponse directly to the Player this Thread is assigned to, and sends the same response with isYourTurn negated to the other player
     */
    private void sendResponseToBothPlayers(Object response, Game game) {
        sendResponse(game.getNextPlayerToMove().getUser(), response);
        sendResponse(game.getOtherPlayer(game.getNextPlayerToMove()).getUser(), response);
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
            throw new IllegalArgumentException();
        }
    }

    private boolean isUserInGame(User user) {
        return getGame(user) != null;
    }

    /**
     * this is necessary when working with a user object from an Action from a client, because the passed in object doesn't have an OutputStream
     */
    private User getUserReference(User user) {
        synchronized (Main.class) {
            List<User> users =Main.getWaitingUsers().stream()
                    .filter(user::equals)
                    .toList();

            if (users.size() == 0) {
                throw new IllegalUserException();
            } else if (users.size() > 1) {
                logger.error("more than one user found, this should never happen");
                throw new IllegalUserException();
            } else return users.get(0);
        }
    }

    private void sendDisconnectResponse(Game game) {
        logger.debug("handling disconnect response");
        synchronized (game) {
            sendResponse(game.getOtherPlayer(getPlayer()).getUser(), new DisconnectResponse(getPlayer()));
        }

    }

    private void handleEndGameAction(EndGameAction endGameAction) {
        Game game = getGame();
        Player self = getPlayerReference(endGameAction.getSelf(), game);

        synchronized (Main.class) {
            synchronized (game) {
                Player player1 = game.getPlayer1();
                Player player2 = game.getPlayer2();

                Main.getWaitingUsers().add(player1.getUser());
                Main.getWaitingUsers().add(player2.getUser());

                Main.getGames().remove(game);

                EndGameResponse endGameResponse = new EndGameResponse("Spieler "+self.getName()+" hat das Spiel beendet");
                sendResponse(game.getOtherPlayer(self).getUser(), endGameResponse);
            }
        }

    }

}
