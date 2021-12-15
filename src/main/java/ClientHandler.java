import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable{
    List <String> games;
    Socket clientSocket;
    String game;
    Logger logger = LoggerFactory.getLogger(getClass());
    public ClientHandler(List<String> games, Socket clientSocket) {
        this.games = games;
        this.clientSocket = clientSocket;
    }
    @Override
    public void run() {
        try {
            while (true) {
                DataInputStream dis = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

                logger.info("dfguisdfohg");
                String str = dis.readUTF();
                logger.info("success: " + str);
                dos.writeUTF(str);
                dos.flush();

            }
        } catch (IOException e) {
            logger.error("the connection was closed",e);
            try {
                clientSocket.close();
            } catch (IOException ex) {
                logger.error("failed to close socket",ex);
            }
            Thread.currentThread().interrupt();
        }


    }
}
