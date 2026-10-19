package com.gberguy.ecpatches.runtime;

public final class AstralFormula {
    public static final String DEFAULT = "prev + 1000 * i * i";
    private interface Node { double evaluate(double i, double prev); }
    private final Node root;

    private AstralFormula(Node root) { this.root = root; }

    public static AstralFormula compile(String expression) {
        if (expression == null || expression.length() > 2048) {
            throw new IllegalArgumentException("Formula must contain at most 2048 characters");
        }
        Parser parser = new Parser(expression);
        Node root = parser.expression();
        parser.space();
        if (parser.position != expression.length()) throw parser.error("Unexpected input");
        return new AstralFormula(root);
    }

    public double evaluate(int i, long prev) { return finite(root.evaluate(i, prev)); }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite arithmetic result (division by zero, domain error, or overflow)");
        return value;
    }

    private static final class Parser {
        private final String text;
        private int position;
        private int depth;
        private int nodes;
        Parser(String text) { this.text = text; }
        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at column " + (position + 1));
        }
        void space() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }
        boolean take(char c) {
            space();
            if (position < text.length() && text.charAt(position) == c) { position++; return true; }
            return false;
        }
        void require(char c) { if (!take(c)) throw error("Expected '" + c + "'"); }
        Node node(Node value) {
            if (++nodes > 512) throw error("Formula exceeds 512 expression nodes");
            return value;
        }
        Node expression() {
            Node result = product();
            while (true) {
                Node left = result;
                if (take('+')) { Node right = product(); result = node((i, p) -> finite(left.evaluate(i, p) + right.evaluate(i, p))); }
                else if (take('-')) { Node right = product(); result = node((i, p) -> finite(left.evaluate(i, p) - right.evaluate(i, p))); }
                else return result;
            }
        }
        Node product() {
            Node result = unary();
            while (true) {
                Node left = result;
                if (take('*')) { Node right = unary(); result = node((i, p) -> finite(left.evaluate(i, p) * right.evaluate(i, p))); }
                else if (take('/')) { Node right = unary(); result = node((i, p) -> finite(left.evaluate(i, p) / right.evaluate(i, p))); }
                else return result;
            }
        }
        Node unary() {
            if (++depth > 64) throw error("Formula nesting exceeds 64");
            try {
                if (take('+')) return unary();
                if (take('-')) { Node operand = unary(); return node((i, p) -> -operand.evaluate(i, p)); }
                Node base = primary();
                if (!take('^')) return base;
                Node exponent = unary();
                return node((i, p) -> finite(StrictMath.pow(base.evaluate(i, p), exponent.evaluate(i, p))));
            } finally { depth--; }
        }
        Node primary() {
            if (take('(')) { Node value = expression(); require(')'); return value; }
            space();
            int start = position;
            if (position < text.length() && (Character.isDigit(text.charAt(position)) || text.charAt(position) == '.')) {
                while (position < text.length() && (Character.isDigit(text.charAt(position)) || text.charAt(position) == '.')) position++;
                if (position < text.length() && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
                    position++;
                    if (position < text.length() && (text.charAt(position) == '+' || text.charAt(position) == '-')) position++;
                    while (position < text.length() && Character.isDigit(text.charAt(position))) position++;
                }
                final double value;
                try { value = finite(Double.parseDouble(text.substring(start, position))); }
                catch (IllegalArgumentException invalid) { throw error("Invalid numeric literal"); }
                return node((i, p) -> value);
            }
            while (position < text.length() && Character.isLetter(text.charAt(position))) position++;
            String name = text.substring(start, position);
            if (name.equals("i")) return node((i, p) -> i);
            if (name.equals("prev")) return node((i, p) -> p);
            if (!name.equals("floor") && !name.equals("ceil") && !name.equals("sqrt") && !name.equals("min") && !name.equals("max")) {
                throw error("Unknown variable/function '" + name + "'");
            }
            require('(');
            Node first = expression();
            if (name.equals("min") || name.equals("max")) {
                require(',');
                Node second = expression();
                require(')');
                return name.equals("min") ? node((i, p) -> StrictMath.min(first.evaluate(i, p), second.evaluate(i, p)))
                        : node((i, p) -> StrictMath.max(first.evaluate(i, p), second.evaluate(i, p)));
            }
            require(')');
            if (name.equals("floor")) return node((i, p) -> StrictMath.floor(first.evaluate(i, p)));
            if (name.equals("ceil")) return node((i, p) -> StrictMath.ceil(first.evaluate(i, p)));
            return node((i, p) -> finite(StrictMath.sqrt(first.evaluate(i, p))));
        }
    }
}
