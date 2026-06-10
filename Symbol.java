import java.util.ArrayList;
import java.util.List;

public class Symbol {
    // Structural classifications based on CLS_* and SK_*
    public enum Kind { SK_VAR, SK_PARAM, SK_FN, SK_STRUCT }

    public String name;             // The identifier name text string extracted from the token
    public Kind kind;               // Symbol type category classification
    public Type type = new Type();  // The data type signature profile of this symbol
    public int depth;               // Scope nesting depth: 0 for global, 1 for function, 2+ for inner blocks
    public int varIdx;              // Index assignment used for memory allocation mapping tracking
    public Symbol owner;            // Pointer reference tracking the structural parent container context

    // Struct and Function nested scope members container tracking blocks
    public List<Symbol> locals = new ArrayList<>();  // Function locals
    public List<Symbol> params = new ArrayList<>();  // Function arguments
    public List<Symbol> members = new ArrayList<>(); // Structure members

    public Symbol(String name, Kind kind, int depth) {
        this.name = name;
        this.kind = kind;
        this.depth = depth;
    }
}