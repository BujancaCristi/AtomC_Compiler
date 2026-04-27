import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private String input;
    private int pos = 0;
    private int line = 1;
    private List<Token> tokens = new ArrayList<>();

    public Lexer(String input){
        this.input = input;
    }

    private Token addToken(Token.Code code){
        Token tk = new Token(code, line);
        tokens.add(tk);
        return tk;
    }

    private void TokenErr(String message){
        throw new LexerException(message, line);
    }

    public void showTokens() {
        if (tokens.isEmpty()) return;

        int currentLine = -1;

        for (Token tk : tokens) {
            if (tk.line != currentLine) {
                currentLine = tk.line;
                System.out.print("\n" + currentLine + ": ");
            }

            System.out.print(tk + " ");
        }
        System.out.println();
    }

    public Token.Code getNextToken() {
        int state = 0;
        int startPos = 0;

        while (true) {
            char ch = (pos < input.length()) ? input.charAt(pos) : '\0';

            switch (state) {
                case 0: // Initial State
                    if (Character.isLetter(ch) || ch == '_') {
                        startPos = pos; pos++; state = 1; // Start ID/Keyword
                    } else if (ch == '0') {
                        startPos = pos; pos++; state = 20; // Potential Hex/Octal/Real
                    } else if (Character.isDigit(ch)) {
                        startPos = pos; pos++; state = 21; // Decimal
                    } else if (ch == '"') {
                        startPos = pos; pos++; state = 40; // String
                    } else if (ch == '\'') {
                        startPos = pos; pos++; state = 45; // Char
                    } else if (ch == '/') {
                        pos++; state = 30; // Comment or DIV
                    } else if (ch == '=') {
                        pos++; state = 3; // ASSIGN or EQUAL
                    } else if (ch == '!') {
                        pos++; state = 10; // NOT or NOTEQ
                    } else if (ch == '&') {
                        pos++; state = 12; // AND (&&)
                    } else if (ch == '|') {
                        pos++; state = 14; // OR (||)
                    } else if (ch == '<') {
                        pos++; state = 16; // LESS or LESSEQ
                    } else if (ch == '>') {
                        pos++; state = 18; // GREATER or GREATEREQ
                    } else if (ch == '+') { pos++; addToken(Token.Code.ADD); return Token.Code.ADD; }
                    else if (ch == '-') { pos++; addToken(Token.Code.SUB); return Token.Code.SUB; }
                    else if (ch == '*') { pos++; addToken(Token.Code.MUL); return Token.Code.MUL; }
                    else if (ch == '.') { pos++; addToken(Token.Code.DOT); return Token.Code.DOT; }
                    else if (ch == ',') { pos++; addToken(Token.Code.COMA); return Token.Code.COMA; }
                    else if (ch == ';') { pos++; addToken(Token.Code.SEMICOLON); return Token.Code.SEMICOLON; }
                    else if (ch == '(') { pos++; addToken(Token.Code.LP); return Token.Code.LP; }
                    else if (ch == ')') { pos++; addToken(Token.Code.RP); return Token.Code.RP; }
                    else if (ch == '{') { pos++; addToken(Token.Code.LA); return Token.Code.LA; }
                    else if (ch == '}') { pos++; addToken(Token.Code.RA); return Token.Code.RA; }
                    else if (ch == '[') { pos++; addToken(Token.Code.LBR); return Token.Code.LBR; }
                    else if (ch == ']') { pos++; addToken(Token.Code.RBR); return Token.Code.RBR; }
                    else if (ch == ' ' || ch == '\r' || ch == '\t') { pos++; } // Skip
                    else if (ch == '\n') { line++; pos++; } // Update line
                    else if (ch == '\0') { addToken(Token.Code.END); return Token.Code.END; }
                    else TokenErr("invalid character: " + ch);
                    break;

                case 1: // ID / Keywords
                    if (Character.isLetterOrDigit(ch) || ch == '_') pos++;
                    else state = 2;
                    break;

                case 2: // ID Final State
                    String text = input.substring(startPos, pos);
                    Token tk;
                    if (text.equals("break")) tk = addToken(Token.Code.BREAK);
                    else if (text.equals("char")) tk = addToken(Token.Code.CHAR);
                    else if (text.equals("int")) tk = addToken(Token.Code.INT);
                    else if (text.equals("double")) tk = addToken(Token.Code.DOUBLE);
                    else if (text.equals("else")) tk = addToken(Token.Code.ELSE);
                    else if (text.equals("for")) tk = addToken(Token.Code.FOR);
                    else if (text.equals("if")) tk = addToken(Token.Code.IF);
                    else if (text.equals("return")) tk = addToken(Token.Code.RETURN);
                    else if (text.equals("struct")) tk = addToken(Token.Code.STRUCT);
                    else if (text.equals("void")) tk = addToken(Token.Code.VOID);
                    else if (text.equals("while")) tk = addToken(Token.Code.WHILE);
                    else {
                        tk = addToken(Token.Code.ID);
                        tk.text = text;
                    }
                    return tk.code;

                case 3: // '=' or '=='
                    if (ch == '=') { pos++; addToken(Token.Code.EQUAL); return Token.Code.EQUAL; }
                    else { addToken(Token.Code.ASSIGN); return Token.Code.ASSIGN; }

                case 10: // '!' or '!='
                    if (ch == '=') { pos++; addToken(Token.Code.NOTEQ); return Token.Code.NOTEQ; }
                    else { addToken(Token.Code.NOT); return Token.Code.NOT; }

                case 12: // '&' -> '&&'
                    if (ch == '&') { pos++; addToken(Token.Code.AND); return Token.Code.AND; }
                    else TokenErr("expected '&'");
                    break;

                case 14: // '|' -> '||'
                    if (ch == '|') { pos++; addToken(Token.Code.OR); return Token.Code.OR; }
                    else TokenErr("expected '|'");
                    break;

                case 16: // '<' or '<='
                    if (ch == '=') { pos++; addToken(Token.Code.LESSEQ); return Token.Code.LESSEQ; }
                    else { addToken(Token.Code.LESS); return Token.Code.LESS; }

                case 18: // '>' or '>='
                    if (ch == '=') { pos++; addToken(Token.Code.GREATEREQ); return Token.Code.GREATEREQ; }
                    else { addToken(Token.Code.GREATER); return Token.Code.GREATER; }

                case 20: // Leading '0' (Hex, Octal, or Deci)
                    if (ch == 'x' || ch == 'X') { pos++; state = 22; } // Hex
                    else if (ch >= '0' && ch <= '7') { state = 23; } // Octal
                    else if (ch == '.') { pos++; state = 24; } // Real
                    else state = 25; // Just 0
                    break;

                case 21: // Decimal sequence
                    if (Character.isDigit(ch)) pos++;
                    else if (ch == '.') { pos++; state = 24; }
                    else state = 25;
                    break;

                case 22: // Hex sequence
                    if (Character.isDigit(ch) || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')) pos++;
                    else {
                        Token t = addToken(Token.Code.CT_INT);
                        t.i = Long.decode(input.substring(startPos, pos));
                        return Token.Code.CT_INT;
                    }
                    break;

                case 23: // Octal sequence
                    if (ch >= '0' && ch <= '7') pos++;
                    else {
                        Token t = addToken(Token.Code.CT_INT);
                        t.i = Long.parseLong(input.substring(startPos, pos), 8);
                        return Token.Code.CT_INT;
                    }
                    break;

                case 24: // Real Number sequence
                    if (Character.isDigit(ch)) {
                        pos++;
                    } else if (ch == 'e' || ch == 'E') {
                        pos++;
                        // Handle optional + or - after E
                        char next = (pos < input.length()) ? input.charAt(pos) : '\0';
                        if (next == '+' || next == '-') pos++;
                    } else {
                        // End of real number
                        Token t = addToken(Token.Code.CT_REAL);
                        t.r = Double.parseDouble(input.substring(startPos, pos));
                        return Token.Code.CT_REAL;
                    }
                    break;

                case 25: // Decimal Final
                    Token t = addToken(Token.Code.CT_INT);
                    t.i = Long.parseLong(input.substring(startPos, pos));
                    return Token.Code.CT_INT;

                case 30: // Comment or Division
                    if (ch == '/') { pos++; state = 31; } // // comment
                    else if (ch == '*') { pos++; state = 32; } // /* comment
                    else { addToken(Token.Code.DIV); return Token.Code.DIV; }
                    break;

                case 31: // Single-line comment
                    if (ch != '\n' && ch != '\r' && ch != '\0') pos++;
                    else state = 0; // Discard and go to start
                    break;

                case 32: // Multi-line comment
                    if (ch == '*') { pos++; state = 33; }
                    else if (ch == '\n') { line++; pos++; }
                    else if (ch == '\0') TokenErr("unterminated comment");
                    else pos++;
                    break;

                case 33: // End of /* comment?
                    if (ch == '/') { pos++; state = 0; }
                    else if (ch == '*') pos++;
                    else { pos++; state = 32; }
                    break;

                case 40: // Inside a String "..."
                    if (ch == '\\') {
                        pos += 2; // Skip the backslash and the escaped character (like \" or \n)
                    } else if (ch == '"') {
                        pos++;
                        Token tok = addToken(Token.Code.CT_STRING);
                        tok.text = input.substring(startPos + 1, pos - 1);
                        return Token.Code.CT_STRING;
                    } else if (ch == '\0') {
                        TokenErr("unterminated string");
                    } else {
                        pos++;
                    }
                    break;

                case 45: // Inside a Char '...'
                    if (ch == '\\') {
                        pos += 2;
                        state = 46;
                    } else if (ch != '\'' && ch != '\0') {
                        pos++;
                        state = 46;
                    } else {
                        TokenErr("invalid character constant");
                    }
                    break;

                case 46: // Closing Quote for Char
                    if (ch == '\'') {
                        pos++;
                        Token tok = addToken(Token.Code.CT_CHAR);
                        tok.i = input.charAt(pos - 2);
                        return Token.Code.CT_CHAR;
                    } else {
                        TokenErr("char constant too long or missing closing quote");
                    }
                    break;
            }
        }
    }
}
