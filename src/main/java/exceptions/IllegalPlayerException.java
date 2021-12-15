package backend.exceptions;

/**
 * Gets thrown when a player tries to do something when he is not allowed to do anything (p.e. when it's not his turn)
 */
public class IllegalPlayerException extends GameException{
    public IllegalPlayerException() {
        super();
    }
}
