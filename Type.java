public class Type {
    // Enum values mimicking TB_* from the documentation
    public enum Base { INT, DOUBLE, CHAR, STRUCT, VOID }

    public Base tb;         // Base type [cite: 282, 315]
    public Symbol structS;  // Reference to the struct definition if tb == STRUCT
    public int nElements = -1; // Negative if non-array; 0 or greater for arrays
}