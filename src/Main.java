import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        String filePath = "/home/cris/info/Y3/Sem2/CT/Atom-C compiler/Atom-C-Compiler/tests/9.c";

        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            Lexer lexer = new Lexer(content);

            while (lexer.getNextToken() != Token.Code.END);
            lexer.showTokens();

        } catch (IOException e) {
            System.err.println("Could not read file: " + e.getMessage());
        } catch (LexerException e) {
            System.err.println(e.getMessage());
        }
    }
}