import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {
    static List<String> games = Collections.synchronizedList(new ArrayList<>());
    public static void main(String[] args) throws IOException {

        Logger logger = LoggerFactory.getLogger(Main.class);
        ServerSocket serverSocket = new ServerSocket(5056);
        Socket currentSocket;
        while(true) {
            try {
                currentSocket = serverSocket.accept();
                logger.debug("A new client is connected");
                Thread handler = new Thread(new ClientHandler(games, currentSocket));
                handler.start();
                //give clientHandler game array
            } catch (IOException e) {

            }



        }
    }
}
