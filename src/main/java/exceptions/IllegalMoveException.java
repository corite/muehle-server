package backend.exceptions;

/**
 * Gets thrown when the player is authorised to move and performs an operation he is allowed to do in his phase,
 * but does so in an illegal way (p.e. moves to a position that is already occupied)
 */
public class IllegalMoveException extends GameException {
    public IllegalMoveException() {
        super();
    }
}
