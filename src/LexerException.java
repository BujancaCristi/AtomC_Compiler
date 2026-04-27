public class LexerException extends RuntimeException{
    public LexerException(String message, int line){
        super("Error in line: " + line + " : " + message);
    }
}
