import java.util.ArrayList;
import java.util.List;

public class SymbolTable {
    private List<Symbol> symbols = new ArrayList<>();
    private int currentDepth = 0; // Tracks the current block nesting level [cite: 302-303, 377]

    public void pushDomain() {
        currentDepth++; // Increment nesting level [cite: 397, 457, 482]
    }

    public int getDepth() {
        return currentDepth;
    }

    public void dropDomain() {
        // Semantic Action requirement: delete local variables when exiting a domain [cite: 380, 403, 461, 484]
        symbols.removeIf(s -> s.depth == currentDepth);
        currentDepth--;
    }

    public void addSymbol(Symbol s) {
        symbols.add(s); // Appends to the symbol table tracking array [cite: 374, 416, 455, 476]
    }

    // Searches from right-to-left to always find the closest valid declaration [cite: 379]
    public Symbol findSymbol(String name) {
        for (int i = symbols.size() - 1; i >= 0; i--) {
            if (symbols.get(i).name.equals(name)) {
                return symbols.get(i);
            }
        }
        return null;
    }

    // Searches strictly inside the current depth level to detect redifinition collisions [cite: 391, 411, 451, 470]
    public Symbol findSymbolInDomain(String name) {
        for (int i = symbols.size() - 1; i >= 0; i--) {
            Symbol s = symbols.get(i);
            if (s.depth < currentDepth) break; // We moved back to a outer domain layer, stop checking
            if (s.name.equals(name)) return s;
        }
        return null;
    }
}