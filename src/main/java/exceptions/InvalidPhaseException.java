package backend.exceptions;

/**
 * Gets thrown when an authorised player tries to perform an action which he is not allowed to in his current phase.
 */
public class InvalidPhaseException extends GameException{
    public InvalidPhaseException() {
        super();
    }
}
