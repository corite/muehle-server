import logic.entities.Game;
import logic.entities.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;


public class Main {
    private static final  List<Game> GAMES = Collections.synchronizedList(new ArrayList<>());
    private static final List<Player> WAITING_PLAYERS = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Integer> NAME_COUNT = Collections.synchronizedMap(new HashMap<>());
    private static final Map<Player,Player> REQUESTED_PAIRS = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) throws IOException {

        Logger logger = LoggerFactory.getLogger(Main.class);
        int port =5056;
        ServerSocket serverSocket = new ServerSocket(port);
        logger.info("the server is listening to requests on port {}",port);
        Socket currentSocket;
        while(true) {
            try {
                currentSocket = serverSocket.accept();
                logger.debug("A new client is connected");
                Thread handler = new Thread(new ClientHandler(currentSocket));
                handler.start();
            } catch (IOException e) {
                logger.error("failed to accept client socket",e);
            }


        }
    }
    public static List<Game> getGames() {
        return GAMES;
    }
    public static List<Player> getWaitingPlayers() {
        return WAITING_PLAYERS;
    }

    public static Map<String, Integer> getNameCount() {
        return NAME_COUNT;
    }

    /**
     * the requesting player is key, the requested player is value
     */
    public static Map<Player, Player> getRequestedPairs() {
        return REQUESTED_PAIRS;
    }
}
