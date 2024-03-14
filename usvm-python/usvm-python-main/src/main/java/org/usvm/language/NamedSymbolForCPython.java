package org.usvm.language;

public class NamedSymbolForCPython {
    public SymbolForCPython symbol;
    public String name;

    public NamedSymbolForCPython(String name, SymbolForCPython symbol) {
        this.symbol = symbol;
        this.name = name;
    }
}
