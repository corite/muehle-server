import logic.entities.Game;
import logic.entities.Player;
import logic.entities.StoneState;
import networking.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable{
    Socket clientSocket;
    Game game;
    Logger logger = LoggerFactory.getLogger(getClass());
    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }
    @Override
    public void run() {
        try {
            while (true) {
                ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());

                Object inputObject = ois.readObject();

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
        logger.info("game");
    }

    private void handleInitialAction(InitialAction initialAction) {
        synchronized (Main.class) {
            logger.debug("handling initial action");
            String name = initialAction.getName();

            int code = Main.getNameCount().getOrDefault(name, 0) +1;//codes will start with value 1
            Main.getNameCount().put(name, code);

            Player self = new Player(name,code, StoneState.NONE);
            Main.getPlayers().add(self);
            InitialResponse initialResponse = new InitialResponse(self);


        }
    }

    private void handleListPlayersAction(ListPlayersAction listPlayersAction) {
        logger.info("list");
    }

    private void handleReconnectAction(ReconnectAction reconnectAction) {
        logger.info("recon");
    }

    private void sendResponse(Player player, Object response) {
        synchronized (player) {

        }
    }
}
