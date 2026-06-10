import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int crtIndex = 0;
    private Token crtTk;
    private Token consumedTk;

    // Domain & Type Analysis State Fields
    private SymbolTable symTable = new SymbolTable();
    private Symbol owner = null;                      // Points to the active function or struct context

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        if (tokens != null && !tokens.isEmpty()) {
            this.crtTk = tokens.get(0);
        }
        addExtFuncs(); // Automatically populate the symbol table with native functions
    }

    // Helper to throw detailed compiler error messages with the current line number
    private void tkerr(String msg) {
        throw new RuntimeException("Syntax Error in line " + crtTk.line + ": " + msg);
    }

    // Advanced token terminal matcher and consumer
    private boolean consume(Token.Code code) {
        if (crtTk.code == code) {
            consumedTk = crtTk;
            crtIndex++;
            if (crtIndex < tokens.size()) {
                crtTk = tokens.get(crtIndex);
            }
            return true;
        }
        return false;
    }

    // Backtracking utility to rewind crtTk if an alternative path fails
    private void restoreState(int indexPosition) {
        this.crtIndex = indexPosition;
        this.crtTk = tokens.get(indexPosition);
    }

    // =========================================================================
    // TYPE CHECKING UTILITIES (Laborator 6)
    // =========================================================================

    // Checks if the evaluated expression can be safely treated as a scalar primitive
    private boolean canBeScalar(RetVal r) {
        return r.type.nElements < 0 && r.type.tb != Type.Base.STRUCT && r.type.tb != Type.Base.VOID;
    }

    // Validates implicit assignment and conversion compatibility rules
    private boolean convTo(Type src, Type dst) {
        if (src.nElements >= 0) { // Source is an array
            return dst.nElements >= 0 && src.tb == dst.tb; // Destination must be an identical array
        }
        if (dst.nElements >= 0) {
            return false; // Cannot convert a non-array to an array
        }
        if (dst.tb == Type.Base.STRUCT) {
            return src.tb == Type.Base.STRUCT && src.structS == dst.structS; // Struct layouts must match
        }
        if (src.tb == Type.Base.STRUCT) {
            return false; // A structure cannot convert to a primitive numeric scalar
        }
        return true; // Primitive scalars can implicitly promote among themselves (char <-> int <-> double)
    }

    // Determines the promotional resulting type matching standard arithmetic operations
    private boolean arithTypeTo(Type s1, Type s2, Type dst) {
        if (s1.nElements >= 0 || s2.nElements >= 0) return false; // Arrays cannot perform arithmetic math
        if (s1.tb == Type.Base.STRUCT || s2.tb == Type.Base.STRUCT) return false; // Struct math is prohibited
        if (s1.tb == Type.Base.VOID || s2.tb == Type.Base.VOID) return false;

        dst.nElements = -1; // Resulting value is always a primitive scalar

        if (s1.tb == Type.Base.DOUBLE || s2.tb == Type.Base.DOUBLE) {
            dst.tb = Type.Base.DOUBLE;
        } else {
            dst.tb = Type.Base.INT; // Fallback promotions down to integer types (char + int -> int)
        }
        return true;
    }

    // =========================================================================
    // SYSTEM PREDEFINED FUNCTIONS INITIALIZATION (Laborator 6)
    // =========================================================================
    private void addExtFuncs() {
        // void put_s(char s[])
        Symbol put_s = addExtFunc("put_s", Type.Base.VOID, -1);
        addFuncArg(put_s, "s", Type.Base.CHAR, 0); // 0 means array without explicit size

        // void get_s(char s[])
        Symbol get_s = addExtFunc("get_s", Type.Base.VOID, -1);
        addFuncArg(get_s, "s", Type.Base.CHAR, 0);

        // void put_i(int i)
        Symbol put_i = addExtFunc("put_i", Type.Base.VOID, -1);
        addFuncArg(put_i, "i", Type.Base.INT, -1);

        // int get_i()
        addExtFunc("get_i", Type.Base.INT, -1);

        // void put_d(double d)
        Symbol put_d = addExtFunc("put_d", Type.Base.VOID, -1);
        addFuncArg(put_d, "d", Type.Base.DOUBLE, -1);

        // double get_d()
        addExtFunc("get_d", Type.Base.DOUBLE, -1);

        // void put_c(char c)
        Symbol put_c = addExtFunc("put_c", Type.Base.VOID, -1);
        addFuncArg(put_c, "c", Type.Base.CHAR, -1);

        // char get_c()
        addExtFunc("get_c", Type.Base.CHAR, -1);

        // double seconds()
        addExtFunc("seconds", Type.Base.DOUBLE, -1);
    }

    private Symbol addExtFunc(String name, Type.Base retBase, int nElements) {
        Symbol s = new Symbol(name, Symbol.Kind.SK_FN, 0); // Predefined functions live at global scope depth 0
        s.type.tb = retBase;
        s.type.nElements = nElements;
        symTable.addSymbol(s);
        return s;
    }

    private void addFuncArg(Symbol func, String name, Type.Base base, int nElements) {
        Symbol arg = new Symbol(name, Symbol.Kind.SK_PARAM, 1); // Parameters live at function depth level 1
        arg.type.tb = base;
        arg.type.nElements = nElements;
        func.params.add(arg); // Sync definition profile structural requirements
    }

    // =========================================================================
    // PARSING PREDICATES WITH SEMANTIC ACTIONS (Laborator 5 & 6)
    // =========================================================================

    // unit: (structDef | fnDef | varDef)* END
    public boolean unit() {
        while (true) {
            if (structDef()) continue;
            if (fnDef()) continue;
            if (varDef()) continue;
            break;
        }
        if (consume(Token.Code.END)) return true;
        tkerr("Unexpected tokens at end of file");
        return false;
    }

    // structDef: STRUCT ID LACC varDef* RACC SEMICOLON
    public boolean structDef() {
        int startTkPos = crtIndex;
        if (!consume(Token.Code.STRUCT)) return false;

        if (!consume(Token.Code.ID)) tkerr("missing structure identifier");
        String structName = consumedTk.text;

        // Scope Check: Structure name must be unique in its domain
        if (symTable.findSymbolInDomain(structName) != null) {
            tkerr("symbol redefinition: " + structName);
        }

        // Create struct symbol and push its local scope domain
        Symbol s = new Symbol(structName, Symbol.Kind.SK_STRUCT, symTable.getDepth());
        s.type.tb = Type.Base.STRUCT;
        s.type.structS = s;
        symTable.addSymbol(s);

        symTable.pushDomain();
        Symbol oldOwner = owner;
        owner = s;

        if (!consume(Token.Code.LA)) tkerr("missing '{' in structure definition");

        while (varDef());

        if (!consume(Token.Code.RA)) tkerr("missing '}' in structure definition");
        if (!consume(Token.Code.SEMICOLON)) tkerr("missing ';' after structure closure");

        // Exit struct domain
        owner = oldOwner;
        symTable.dropDomain();
        return true;
    }

    // varDef: typeBase ID arrayDecl? SEMICOLON
    public boolean varDef() {
        int startTkPos = crtIndex;
        Type t = new Type(); // Synthesized attribute placeholder

        if (!typeBase(t)) return false;

        if (consume(Token.Code.ID)) {
            String varName = consumedTk.text;

            if (arrayDecl(t)) {
                // Scope Check: Array variables must have a specified size
                if (t.nElements == 0) {
                    tkerr("a vector variable must have a specified dimension (e.g., v[10])");
                }
            }

            if (consume(Token.Code.SEMICOLON)) {
                // Scope Check: Variable name must be unique in this domain
                if (symTable.findSymbolInDomain(varName) != null) {
                    tkerr("symbol redefinition: " + varName);
                }

                Symbol var = new Symbol(varName, Symbol.Kind.SK_VAR, symTable.getDepth());
                var.type = t;
                var.owner = owner;
                symTable.addSymbol(var);

                // Track allocations inside active parent blocks
                if (owner != null) {
                    if (owner.kind == Symbol.Kind.SK_FN) {
                        var.varIdx = owner.locals.size();
                        owner.locals.add(var);
                    } else if (owner.kind == Symbol.Kind.SK_STRUCT) {
                        var.varIdx = owner.members.size();
                        owner.members.add(var);
                    }
                }
                return true;
            } else {
                tkerr("missing ';' after variable declaration");
            }
        }
        restoreState(startTkPos);
        return false;
    }

    // typeBase: INT | DOUBLE | CHAR | STRUCT ID
    public boolean typeBase(Type t) {
        t.nElements = -1; // Default configuration to a scalar representation

        if (consume(Token.Code.INT)) { t.tb = Type.Base.INT; return true; }
        if (consume(Token.Code.DOUBLE)) { t.tb = Type.Base.DOUBLE; return true; }
        if (consume(Token.Code.CHAR)) { t.tb = Type.Base.CHAR; return true; }

        int startTkPos = crtIndex;
        if (consume(Token.Code.STRUCT)) {
            if (consume(Token.Code.ID)) {
                String structName = consumedTk.text;
                Symbol s = symTable.findSymbol(structName);

                // Type Check: Struct layout type must be declared previously
                if (s == null || s.kind != Symbol.Kind.SK_STRUCT) {
                    tkerr("structura nedefinita: " + structName);
                }
                t.tb = Type.Base.STRUCT;
                t.structS = s;
                return true;
            }
        }
        restoreState(startTkPos);
        return false;
    }

    // arrayDecl: LBRACKET CT_INT? RBRACKET
    public boolean arrayDecl(Type t) {
        if (!consume(Token.Code.LBR)) return false;
        if (consume(Token.Code.CT_INT)) {
            t.nElements = (int) consumedTk.i;
        } else {
            t.nElements = 0;
        }
        if (consume(Token.Code.RBR)) return true;
        else tkerr("missing ']' in array selector declaration");
        return false;
    }

    // fnDef: (typeBase | VOID) ID LPAR (fnParam (COMMA fnParam)*)? RPAR stmCompound
    public boolean fnDef() {
        int startTkPos = crtIndex;
        Type t = new Type();

        boolean baseCheck = typeBase(t) || consume(Token.Code.VOID);
        if (!baseCheck) return false;

        if (consumedTk.code == Token.Code.VOID) {
            t.tb = Type.Base.VOID;
        }

        if (consume(Token.Code.ID)) {
            String fnName = consumedTk.text;

            // Scope Check: Function identifier must be unique
            if (symTable.findSymbolInDomain(fnName) != null) {
                tkerr("symbol redefinition: " + fnName);
            }

            Symbol fn = new Symbol(fnName, Symbol.Kind.SK_FN, symTable.getDepth());
            fn.type = t;
            symTable.addSymbol(fn);

            Symbol oldOwner = owner;
            owner = fn;
            symTable.pushDomain(); // Local scope domain begins IMMEDIATELY after LPAR

            if (consume(Token.Code.LP)) {
                if (fnParam()) {
                    while (consume(Token.Code.COMA)) {
                        if (!fnParam()) tkerr("missing parameter definition trailing ','");
                    }
                }
                if (consume(Token.Code.RP)) {
                    // Critical: pass 'false' to prevent stmCompound from opening a duplicate scope depth layer
                    if (stmCompound(false)) {
                        symTable.dropDomain();
                        owner = oldOwner;
                        return true;
                    }
                } else {
                    tkerr("missing closure matching mark ')' in function signature header");
                }
            }
        }
        restoreState(startTkPos);
        return false;
    }

    // fnParam: typeBase ID arrayDecl?
    public boolean fnParam() {
        int startTkPos = crtIndex;
        Type t = new Type();
        if (!typeBase(t)) return false;

        if (consume(Token.Code.ID)) {
            String paramName = consumedTk.text;

            if (arrayDecl(t)) {
                t.nElements = 0; // Constraint: convert parameter arrays to unsized arrays (int v[10] -> int v[])
            }

            // Scope Check: Parameter identifier must be unique
            if (symTable.findSymbolInDomain(paramName) != null) {
                tkerr("symbol redefinition: " + paramName);
            }

            Symbol param = new Symbol(paramName, Symbol.Kind.SK_PARAM, symTable.getDepth());
            param.type = t;
            symTable.addSymbol(param); // Add to dynamic table lookup collections
            owner.params.add(param);   // Sync function signature constraints array field
            return true;
        }
        restoreState(startTkPos);
        return false;
    }

    // stmCompound [in bool newDomain]
    public boolean stmCompound(boolean newDomain) {
        if (!consume(Token.Code.LA)) return false;

        if (newDomain) {
            symTable.pushDomain();
        }

        while (true) {
            if (varDef()) continue;
            if (stm()) continue;
            break;
        }

        if (consume(Token.Code.RA)) {
            if (newDomain) {
                symTable.dropDomain();
            }
            return true;
        } else {
            tkerr("missing statement matching enclosure wrapper curly bracket sign '}'");
        }
        return false;
    }

    // stm: stmCompound | IF | WHILE | FOR | BREAK | RETURN | expr? SEMICOLON
    public boolean stm() {
        int startTkPos = crtIndex;

        // Standard compound code scopes explicitly prompt true to create isolated block domains
        if (stmCompound(true)) return true;

        RetVal rCond = new RetVal();
        RetVal rInit = new RetVal();
        RetVal rStep = new RetVal();
        RetVal rExpr = new RetVal();

        // IF LPAR expr RPAR stm (ELSE stm)?
        if (consume(Token.Code.IF)) {
            if (!consume(Token.Code.LP)) tkerr("missing '(' after 'if'");
            if (!expr(rCond)) tkerr("invalid expression inside 'if'");

            // Type Check: IF condition must be a scalar value
            if (!canBeScalar(rCond)) tkerr("the if condition must be a scalar value");

            if (!consume(Token.Code.RP)) tkerr("missing closing ')' after 'if'");
            if (!stm()) tkerr("missing branch statement after 'if'");
            if (consume(Token.Code.ELSE)) {
                if (!stm()) tkerr("missing branch execution logic inside 'else'");
            }
            return true;
        }

        // WHILE LPAR expr RPAR stm
        if (consume(Token.Code.WHILE)) {
            if (!consume(Token.Code.LP)) tkerr("missing '(' after 'while'");
            if (!expr(rCond)) tkerr("invalid loop expression assigned to 'while'");

            // Type Check: WHILE condition must be a scalar value
            if (!canBeScalar(rCond)) tkerr("the while condition must be a scalar value");

            if (!consume(Token.Code.RP)) tkerr("missing loop closure ')'");
            if (!stm()) tkerr("missing executable body context inside 'while' loop");
            return true;
        }

        // FOR LPAR expr? SEMICOLON expr? SEMICOLON expr? RPAR stm
        if (consume(Token.Code.FOR)) {
            if (!consume(Token.Code.LP)) tkerr("missing '(' after 'for'");
            expr(rInit);
            if (!consume(Token.Code.SEMICOLON)) tkerr("missing ';' after init expression inside 'for'");

            if (expr(rCond)) {
                // Type Check: FOR loop limit condition statement must evaluate as a scalar primitive
                if (!canBeScalar(rCond)) tkerr("the for condition must be a scalar value");
            }

            if (!consume(Token.Code.SEMICOLON)) tkerr("missing ';' after condition expression inside 'for'");
            expr(rStep);
            if (!consume(Token.Code.RP)) tkerr("missing closing ')' after loop header rules inside 'for'");
            if (!stm()) tkerr("missing code execution body assigned inside 'for' loop");
            return true;
        }

        // BREAK SEMICOLON
        if (consume(Token.Code.BREAK)) {
            if (consume(Token.Code.SEMICOLON)) return true;
            else tkerr("missing statement closure ';'");
        }

        // RETURN expr? SEMICOLON
        if (consume(Token.Code.RETURN)) {
            if (expr(rExpr)) {
                // Type Check: Void functions cannot return values
                if (owner.type.tb == Type.Base.VOID) tkerr("a void function cannot return a value");
                // Type Check: Return types must be scalar primitives
                if (!canBeScalar(rExpr)) tkerr("the return value must be a scalar value");
                // Type Check: Evaluated type must implicitly match function signatures
                if (!convTo(rExpr.type, owner.type)) {
                    tkerr("cannot convert the return expression type to the function return type");
                }
            } else {
                // Type Check: Non-void expressions must supply a returned argument value
                if (owner.type.tb != Type.Base.VOID) tkerr("a non-void function must return a value");
            }

            if (consume(Token.Code.SEMICOLON)) return true;
            else tkerr("missing instruction termination sequence identifier ';'");
        }

        // expr? SEMICOLON
        if (expr(rExpr)) {
            if (consume(Token.Code.SEMICOLON)) return true;
            else tkerr("missing statement punctuation terminator ';'");
        } else if (consume(Token.Code.SEMICOLON)) {
            return true; // Bypasses an empty line semicolon cleanly!
        }

        restoreState(startTkPos);
        return false;
    }

    // expr: exprAssign
    public boolean expr(RetVal r) {
        return exprAssign(r);
    }

    // exprAssign: exprUnary ASSIGN exprAssign | exprOr
    public boolean exprAssign(RetVal r) {
        int startTkPos = crtIndex;
        RetVal rDst = new RetVal(); // Attribute tracking placeholder for the left-hand side

        if (exprUnary(rDst)) {
            if (consume(Token.Code.ASSIGN)) {
                if (exprAssign(r)) {
                    // Type Checks for safe memory mutation bounds assignment rules
                    if (!rDst.isLVal) tkerr("the assign destination must be a left-value");
                    if (rDst.isCtVal) tkerr("the assign destination cannot be constant");
                    if (!canBeScalar(rDst)) tkerr("the assign destination must be scalar");
                    if (!canBeScalar(r)) tkerr("the assign source must be scalar");
                    if (!convTo(r.type, rDst.type)) {
                        tkerr("the assign source cannot be converted to destination");
                    }

                    // Assign evaluated synthesized statuses changes
                    r.isLVal = false;
                    r.isCtVal = true;
                    return true;
                } else {
                    tkerr("missing value expression target right of operator '='");
                }
            }
        }
        restoreState(startTkPos);
        return exprOr(r);
    }

    // exprOr: exprOr OR exprAnd | exprAnd (Loop structural optimization version)
    public boolean exprOr(RetVal r) {
        if (exprAnd(r)) {
            while (consume(Token.Code.OR)) {
                RetVal right = new RetVal();
                if (!exprAnd(right)) tkerr("missing expression trailing logical operator '||'");

                Type tDst = new Type();
                // Type Check: Both operands must be scalar and not structures
                if (!arithTypeTo(r.type, right.type, tDst)) tkerr("invalid operand type for ||");

                // Result is always a non-lval compile-time int value
                r.type = new Type(); r.type.tb = Type.Base.INT; r.type.nElements = -1;
                r.isLVal = false;
                r.isCtVal = true;
            }
            return true;
        }
        return false;
    }

    // exprAnd: exprAnd AND exprEq | exprEq (Loop structural optimization version)
    public boolean exprAnd(RetVal r) {
        if (exprEq(r)) {
            while (consume(Token.Code.AND)) {
                RetVal right = new RetVal();
                if (!exprEq(right)) tkerr("missing expression trailing logical operator '&&'");

                Type tDst = new Type();
                // Type Check: Both operands must be scalar and not structures
                if (!arithTypeTo(r.type, right.type, tDst)) tkerr("invalid operand type for &&");

                r.type = new Type(); r.type.tb = Type.Base.INT; r.type.nElements = -1;
                r.isLVal = false;
                r.isCtVal = true;
            }
            return true;
        }
        return false;
    }

    // exprEq: exprEq (EQUAL | NOTEQ) exprRel | exprRel (Loop structural optimization version)
    public boolean exprEq(RetVal r) {
        if (exprRel(r)) {
            while (consume(Token.Code.EQUAL) || consume(Token.Code.NOTEQ)) {
                RetVal right = new RetVal();
                if (!exprRel(right)) tkerr("missing expression directly right of equality operator");

                Type tDst = new Type();
                // Type Check: Equation arguments must be scalar comparables
                if (!arithTypeTo(r.type, right.type, tDst)) tkerr("invalid operand type for == or !=");

                r.type = new Type(); r.type.tb = Type.Base.INT; r.type.nElements = -1;
                r.isLVal = false;
                r.isCtVal = true;
            }
            return true;
        }
        return false;
    }

    // exprRel: exprRel (LESS | LESSEQ | GREATER | GREATEREQ) exprAdd | exprAdd (Loop structural optimization version)
    public boolean exprRel(RetVal r) {
        if (exprAdd(r)) {
            while (consume(Token.Code.LESS) || consume(Token.Code.LESSEQ) ||
                    consume(Token.Code.GREATER) || consume(Token.Code.GREATEREQ)) {
                RetVal right = new RetVal();
                if (!exprAdd(right)) tkerr("missing numerical parameter following relational operator");

                Type tDst = new Type();
                // Type Check: Relational operations are restricted to primitive scalars
                if (!arithTypeTo(r.type, right.type, tDst)) tkerr("invalid operand type for <, <=, >, >=");

                r.type = new Type(); r.type.tb = Type.Base.INT; r.type.nElements = -1;
                r.isLVal = false;
                r.isCtVal = true;
            }
            return true;
        }
        return false;
    }

    // exprAdd: exprAdd (ADD | SUB) exprMul | exprMul (Loop structural optimization version)
    public boolean exprAdd(RetVal r) {
        if (exprMul(r)) {
            while (consume(Token.Code.ADD) || consume(Token.Code.SUB)) {
                RetVal right = new RetVal();
                if (!exprMul(right)) tkerr("missing evaluation value right of arithmetic operator");

                Type tDst = new Type();
                // Type Check: Math elements must track matching numeric properties
                if (!arithTypeTo(r.type, right.type, tDst)) tkerr("invalid operand type for + or -");

                r.type = tDst;
                r.isLVal = false;
                r.isCtVal = true;
            }
            return true;
        }
        return false;
    }

    // exprMul: exprMul (MUL | DIV) exprCast | exprCast (Loop structural optimization version)
    public boolean exprMul(RetVal r) {
        if (exprCast(r)) {
            while (consume(Token.Code.MUL) || consume(Token.Code.DIV)) {
                RetVal right = new RetVal();
                if (!exprCast(right)) tkerr("missing calculation argument right of operator");

                Type tDst = new Type();
                // Type Check: Division/multiplication restrictions apply to primitives
                if (!arithTypeTo(r.type, right.type, tDst)) tkerr("invalid operand type for * or /");

                r.type = tDst;
                r.isLVal = false;
                r.isCtVal = true;
            }
            return true;
        }
        return false;
    }

    // exprCast: LPAR typeBase arrayDecl? RPAR exprCast | exprUnary
    public boolean exprCast(RetVal r) {
        int startTkPos = crtIndex;
        if (consume(Token.Code.LP)) {
            Type t = new Type();
            RetVal op = new RetVal();

            if (typeBase(t)) {
                arrayDecl(t); // Optional evaluation element context
                if (consume(Token.Code.RP)) {
                    if (exprCast(op)) {
                        // Type Checks: Explicit target conversion restrictions
                        if (t.tb == Type.Base.STRUCT) tkerr("cannot convert to a struct type");
                        if (op.type.tb == Type.Base.STRUCT) tkerr("cannot convert a struct");
                        if (op.type.nElements >= 0 && t.nElements < 0) {
                            tkerr("an array can be converted only to another array");
                        }
                        if (op.type.nElements < 0 && t.nElements >= 0) {
                            tkerr("a scalar can be converted only to another scalar");
                        }

                        r.type = t;
                        r.isLVal = false;
                        r.isCtVal = true;
                        return true;
                    }
                }
            }
        }
        restoreState(startTkPos);
        return exprUnary(r);
    }

    // exprUnary: (SUB | NOT) exprUnary | exprPostfix
    public boolean exprUnary(RetVal r) {
        if (consume(Token.Code.SUB) || consume(Token.Code.NOT)) {
            if (exprUnary(r)) {
                // Type Check: Operands must evaluate cleanly down into a primitive scalar
                if (!canBeScalar(r)) tkerr("unary must have a scalar operand");

                r.isLVal = false; // Unary transformations produce computed r-values
                r.isCtVal = true;
                return true;
            } else {
                tkerr("missing transformation target following prefix operator");
            }
        }
        return exprPostfix(r);
    }

    // exprPostfix: exprPostfix LBRACKET expr RBRACKET | exprPostfix DOT ID | exprPrimary
    public boolean exprPostfix(RetVal r) {
        if (exprPrimary(r)) {
            while (true) {
                // Index lookup sequence evaluation structural rules
                if (consume(Token.Code.LBR)) {
                    RetVal idx = new RetVal();

                    // =================================================================
                    // CUSTOM RESTRICTION: Lock brackets to a singular ID or CT_INT
                    // =================================================================
                    if (crtTk.code == Token.Code.ID || crtTk.code == Token.Code.CT_INT) {
                        consume(crtTk.code);
                    } else {
                        tkerr("Array index must be a single variable identifier or an integer constant (e.g., v[i] or v[1]). Expressions are forbidden.");
                    }

                    if (!consume(Token.Code.RBR)) {
                        tkerr("Expected closing bracket ']' immediately after the index variable or constant.");
                    }

                    // Type Check: Verify that the parent element is an indexable array
                    if (r.type.nElements < 0) tkerr("only an array can be indexed");

                    Type tInt = new Type(); tInt.tb = Type.Base.INT; tInt.nElements = -1;
                    // Type Check: Index must convert to int
                    if (!convTo(idx.type, tInt)) tkerr("the index is not convertible to int");

                    // Propagate the types outward
                    r.type.nElements = -1; // Indexing arrays yields localized scalar primitives
                    r.isLVal = true;       // Index selectors yield address-modifiable left-values
                    r.isCtVal = false;
                    continue;
                }

                // Struct property accessor sequencing evaluations rules
                if (consume(Token.Code.DOT)) {
                    if (!consume(Token.Code.ID)) tkerr("missing structural member identifier right of selector operator '.'");
                    String fieldName = consumedTk.text;

                    // Type Check: Target accessor left elements must represent an evaluated structure
                    if (r.type.tb != Type.Base.STRUCT) tkerr("a field can only be selected from a struct");

                    // Find member element in list from domain configuration models definitions records
                    Symbol field = null;
                    for (Symbol m : r.type.structS.members) {
                        if (m.name.equals(fieldName)) {
                            field = m;
                            break;
                        }
                    }
                    if (field == null) {
                        tkerr("the structure " + r.type.structS.name + " does not have a field " + fieldName);
                    }

                    r.type = field.type;
                    r.isLVal = true;     // Struct fields are modifiable left-values
                    r.isCtVal = field.type.nElements >= 0;
                    continue;
                }
                break;
            }
            return true;
        }
        return false;
    }

    // exprPrimary: ID (LPAR (expr (COMMA expr)*)? RPAR)? | CT_INT | CT_REAL | CT_CHAR | CT_STRING | LPAR expr RPAR
    public boolean exprPrimary(RetVal r) {
        int startTkPos = crtIndex;

        if (consume(Token.Code.ID)) {
            String name = consumedTk.text;
            Symbol s = symTable.findSymbol(name);

            // Scope Check: Identifier must be defined previously
            if (s == null) tkerr("undefined id: " + name);

            if (consume(Token.Code.LP)) { // Function Call Evaluation
                // Type Check: Only symbols of kind SK_FN can be called
                if (s.kind != Symbol.Kind.SK_FN) {
                    tkerr("only a function can be called");
                }

                int paramIdx = 0;
                RetVal rArg = new RetVal(); // Call arguments storage receiver

                // If function has arguments
                if (expr(rArg)) {
                    // Type Check: Argument counting mismatch verification
                    if (paramIdx >= s.params.size()) tkerr("too many arguments in function call");
                    // Type Check: Argument must be implicitly convertible to parameter expectations
                    if (!convTo(rArg.type, s.params.get(paramIdx).type)) {
                        tkerr("in call, cannot convert the argument type to the parameter type");
                    }
                    paramIdx++;

                    while (consume(Token.Code.COMA)) {
                        rArg = new RetVal();
                        if (!expr(rArg)) tkerr("missing parameter argument trailing character ','");
                        if (paramIdx >= s.params.size()) tkerr("too many arguments in function call");
                        if (!convTo(rArg.type, s.params.get(paramIdx).type)) {
                            tkerr("in call, cannot convert the argument type to the parameter type");
                        }
                        paramIdx++;
                    }
                }

                if (!consume(Token.Code.RP)) tkerr("missing closure mapping mark ')' in invocation list");
                // Type Check: Argument counting mismatch verification
                if (paramIdx < s.params.size()) tkerr("too few arguments in function call");

                r.type = s.type;
                r.isLVal = false; // Function calls yield r-values
                r.isCtVal = true;
                return true;
            }

            // Standard Variable Identifier Verification
            if (s.kind == Symbol.Kind.SK_FN) tkerr("a function can only be called");

            // CRITICAL DEEP COPY: Isolates r.type so downstream structural adjustments (like indexing arrays)
            // do not corrupt and alter the footprint saved in the shared lookup Symbol Table
            r.type = new Type();
            r.type.tb = s.type.tb;
            r.type.structS = s.type.structS;
            r.type.nElements = s.type.nElements;

            r.isLVal = true; // Primitive variables are addressable Left-Values
            r.isCtVal = s.type.nElements >= 0; // Arrays match compilation constants tracking rules
            return true;
        }

        // Terminal Constants Extractions
        if (consume(Token.Code.CT_INT)) {
            r.type = new Type(); r.type.tb = Type.Base.INT; r.type.nElements = -1;
            r.isLVal = false; r.isCtVal = true; r.i = consumedTk.i;
            return true;
        }
        if (consume(Token.Code.CT_REAL)) {
            r.type = new Type(); r.type.tb = Type.Base.DOUBLE; r.type.nElements = -1;
            r.isLVal = false; r.isCtVal = true; r.d = consumedTk.r;
            return true;
        }
        if (consume(Token.Code.CT_CHAR)) {
            r.type = new Type(); r.type.tb = Type.Base.CHAR; r.type.nElements = -1;
            r.isLVal = false; r.isCtVal = true; r.i = consumedTk.i;
            return true;
        }
        if (consume(Token.Code.CT_STRING)) {
            r.type = new Type(); r.type.tb = Type.Base.CHAR; r.type.nElements = 0;
            r.isLVal = false; r.isCtVal = true; r.str = consumedTk.text;
            return true;
        }

        // Parentheses Sub-expressions Evaluation Handling
        if (consume(Token.Code.LP)) {
            if (!expr(r)) tkerr("missing explicit calculations expression context inside nested parent block");
            if (consume(Token.Code.RP)) return true;
            else tkerr("missing context enclosure brackets wrapper token character ')'");
        }

        restoreState(startTkPos);
        return false;
    }
}