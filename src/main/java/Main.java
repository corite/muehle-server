import logic.entities.Game;
import logic.entities.Player;

import logic.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;


public class Main {
    private static final DatabaseHandler databaseHandler = new DatabaseHandler();
    private static final  List<Game> GAMES = Collections.synchronizedList(new ArrayList<>());
    private static final List<User> WAITING_USERS = Collections.synchronizedList(new ArrayList<>());
    private static final Map<User,User> REQUESTED_PAIRS = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) throws IOException {

        Logger logger = LoggerFactory.getLogger(Main.class);
        int port =5056;
        ServerSocket serverSocket = new ServerSocket(port);
        logger.info("the server is now listening to requests on port {}",port);
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
    public static List<User> getWaitingUsers() {
        return WAITING_USERS;
    }

    /**
     * the requesting player is key, the requested player is value
     */
    public static Map<User, User> getRequestedPairs() {
        return REQUESTED_PAIRS;
    }

    public static DatabaseHandler getDatabaseHandler() {
        return databaseHandler;
    }
}
