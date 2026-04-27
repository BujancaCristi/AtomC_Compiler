public class Token {
    public enum Code{
        CT_INT,
        CT_REAL,
        CT_CHAR,
        CT_STRING,

        ID,
        BREAK, CHAR, DOUBLE, ELSE, FOR, IF, INT, RETURN, STRUCT, VOID, WHILE,

        ADD, SUB, MUL, DIV,
        ASSIGN, EQUAL, NOTEQ,
        AND, OR, NOT,
        LESS, LESSEQ,
        GREATER, GREATEREQ,
        COMMENT, DOT,

        COMA, SEMICOLON,
        RP, LP,
        RBR, LBR,
        RA, LA,
        END
    }

    public Code code;
    public int line;

    public String text; // everything else
    public long i; // INT no
    public double r; // REAL no

    public Token(Code code, int line){
        this.code = code;
        this.line = line;
    }

    public String toString(){
        String atr = (text != null) ? ":" + text : (code == Code.CT_INT ? ":" + i : "");
        return code + atr;
    }
}
