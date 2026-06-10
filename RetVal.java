public class RetVal {
    public Type type = new Type(); // Data type of the result sub-expression [cite: 536, 545]
    public boolean isLVal;         // True if the target expression is a Left-Value [cite: 539, 546]
    public boolean isCtVal;        // True if it represents a literal constant [cite: 540, 547]

    // Primitive storage placeholders for constants tracking [cite: 524-534, 542-543]
    public long i;                 // int, char
    public double d;               // double
    public String str;             // char[] (string literal)

    public RetVal() {}

    // Convenience constructor for fast token generation actions [cite: 676, 684, 694]
    public RetVal(Type.Base base, int nElements, boolean isLVal, boolean isCtVal) {
        this.type.tb = base;
        this.type.nElements = nElements;
        this.isLVal = isLVal;
        this.isCtVal = isCtVal;
    }
}