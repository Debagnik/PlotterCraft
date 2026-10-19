package in.debagnik.catplotlib.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and translates LaTeX mathematical expressions ($...$, $$...$$, \(...\), \[...\])
 * into high-fidelity Unicode and single-stroke compatible glyph strings.
 * Accurately handles integrals, limits, fractions, radicals, powers, subscripts,
 * Greek alphabets, and mathematical operators.
 */
@Service
public class MathExpressionParserService {

    private static final Map<String, String> GREEK_SYMBOLS = new HashMap<>();
    private static final Map<String, String> MATH_OPERATORS = new HashMap<>();
    private static final Map<Character, Character> SUPERSCRIPTS = new HashMap<>();
    private static final Map<Character, Character> SUBSCRIPTS = new HashMap<>();

    static {
        // Greek Alphabet
        GREEK_SYMBOLS.put("alpha", "α");
        GREEK_SYMBOLS.put("beta", "β");
        GREEK_SYMBOLS.put("gamma", "γ");
        GREEK_SYMBOLS.put("Gamma", "Γ");
        GREEK_SYMBOLS.put("delta", "δ");
        GREEK_SYMBOLS.put("Delta", "Δ");
        GREEK_SYMBOLS.put("epsilon", "ε");
        GREEK_SYMBOLS.put("varepsilon", "ε");
        GREEK_SYMBOLS.put("zeta", "ζ");
        GREEK_SYMBOLS.put("eta", "η");
        GREEK_SYMBOLS.put("theta", "θ");
        GREEK_SYMBOLS.put("vartheta", "θ");
        GREEK_SYMBOLS.put("Theta", "Θ");
        GREEK_SYMBOLS.put("iota", "ι");
        GREEK_SYMBOLS.put("kappa", "κ");
        GREEK_SYMBOLS.put("lambda", "λ");
        GREEK_SYMBOLS.put("Lambda", "Λ");
        GREEK_SYMBOLS.put("mu", "μ");
        GREEK_SYMBOLS.put("nu", "ν");
        GREEK_SYMBOLS.put("xi", "ξ");
        GREEK_SYMBOLS.put("Xi", "Ξ");
        GREEK_SYMBOLS.put("pi", "π");
        GREEK_SYMBOLS.put("varpi", "π");
        GREEK_SYMBOLS.put("Pi", "Π");
        GREEK_SYMBOLS.put("rho", "ρ");
        GREEK_SYMBOLS.put("varrho", "ρ");
        GREEK_SYMBOLS.put("sigma", "σ");
        GREEK_SYMBOLS.put("varsigma", "σ");
        GREEK_SYMBOLS.put("Sigma", "Σ");
        GREEK_SYMBOLS.put("tau", "τ");
        GREEK_SYMBOLS.put("upsilon", "υ");
        GREEK_SYMBOLS.put("Upsilon", "Υ");
        GREEK_SYMBOLS.put("phi", "φ");
        GREEK_SYMBOLS.put("varphi", "φ");
        GREEK_SYMBOLS.put("Phi", "Φ");
        GREEK_SYMBOLS.put("chi", "χ");
        GREEK_SYMBOLS.put("psi", "ψ");
        GREEK_SYMBOLS.put("Psi", "Ψ");
        GREEK_SYMBOLS.put("omega", "ω");
        GREEK_SYMBOLS.put("Omega", "Ω");

        // Mathematical Operators & Symbols
        MATH_OPERATORS.put("int", "∫");
        MATH_OPERATORS.put("iint", "∬");
        MATH_OPERATORS.put("iiint", "∭");
        MATH_OPERATORS.put("oint", "∮");
        MATH_OPERATORS.put("sum", "∑");
        MATH_OPERATORS.put("prod", "∏");
        MATH_OPERATORS.put("approx", "≈");
        MATH_OPERATORS.put("pm", "±");
        MATH_OPERATORS.put("mp", "∓");
        MATH_OPERATORS.put("times", "×");
        MATH_OPERATORS.put("div", "÷");
        MATH_OPERATORS.put("cdot", "·");
        MATH_OPERATORS.put("ast", "*");
        MATH_OPERATORS.put("star", "*");
        MATH_OPERATORS.put("neq", "≠");
        MATH_OPERATORS.put("ne", "≠");
        MATH_OPERATORS.put("leq", "≤");
        MATH_OPERATORS.put("le", "≤");
        MATH_OPERATORS.put("geq", "≥");
        MATH_OPERATORS.put("ge", "≥");
        MATH_OPERATORS.put("in", "∈");
        MATH_OPERATORS.put("notin", "∉");
        MATH_OPERATORS.put("infty", "∞");
        MATH_OPERATORS.put("partial", "∂");
        MATH_OPERATORS.put("nabla", "∇");
        MATH_OPERATORS.put("degree", "°");
        MATH_OPERATORS.put("circ", "°");
        MATH_OPERATORS.put("to", "→");
        MATH_OPERATORS.put("rightarrow", "→");
        MATH_OPERATORS.put("leftarrow", "←");
        MATH_OPERATORS.put("leftrightarrow", "↔");
        MATH_OPERATORS.put("implies", "→");
        MATH_OPERATORS.put("iff", "↔");
        MATH_OPERATORS.put("forall", "∀");
        MATH_OPERATORS.put("exists", "∃");
        MATH_OPERATORS.put("cup", "∪");
        MATH_OPERATORS.put("cap", "∩");
        MATH_OPERATORS.put("subset", "⊂");
        MATH_OPERATORS.put("subseteq", "⊆");

        // Superscript Mappings
        SUPERSCRIPTS.put('0', '⁰');
        SUPERSCRIPTS.put('1', '¹');
        SUPERSCRIPTS.put('2', '²');
        SUPERSCRIPTS.put('3', '³');
        SUPERSCRIPTS.put('4', '⁴');
        SUPERSCRIPTS.put('5', '⁵');
        SUPERSCRIPTS.put('6', '⁶');
        SUPERSCRIPTS.put('7', '⁷');
        SUPERSCRIPTS.put('8', '⁸');
        SUPERSCRIPTS.put('9', '⁹');
        SUPERSCRIPTS.put('+', '⁺');
        SUPERSCRIPTS.put('-', '⁻');
        SUPERSCRIPTS.put('=', '⁼');
        SUPERSCRIPTS.put('(', '⁽');
        SUPERSCRIPTS.put(')', '⁾');
        SUPERSCRIPTS.put('n', 'ⁿ');
        SUPERSCRIPTS.put('i', 'ⁱ');
        SUPERSCRIPTS.put('x', 'ˣ');
        SUPERSCRIPTS.put('∞', '∞');

        // Subscript Mappings
        SUBSCRIPTS.put('0', '₀');
        SUBSCRIPTS.put('1', '₁');
        SUBSCRIPTS.put('2', '₂');
        SUBSCRIPTS.put('3', '₃');
        SUBSCRIPTS.put('4', '₄');
        SUBSCRIPTS.put('5', '₅');
        SUBSCRIPTS.put('6', '₆');
        SUBSCRIPTS.put('7', '₇');
        SUBSCRIPTS.put('8', '₈');
        SUBSCRIPTS.put('9', '₉');
        SUBSCRIPTS.put('+', '₊');
        SUBSCRIPTS.put('-', '₋');
        SUBSCRIPTS.put('=', '₌');
        SUBSCRIPTS.put('(', '₍');
        SUBSCRIPTS.put(')', '₎');
        SUBSCRIPTS.put('a', 'ₐ');
        SUBSCRIPTS.put('e', 'ₑ');
        SUBSCRIPTS.put('o', 'ₒ');
        SUBSCRIPTS.put('x', 'ₓ');
        SUBSCRIPTS.put('i', 'ᵢ');
        SUBSCRIPTS.put('j', 'ⱼ');
        SUBSCRIPTS.put('∞', '∞');
    }

    /**
     * Finds and translates all LaTeX math delimiters ($...$, $$...$$, \(...\), \[...\])
     * embedded within Markdown text into clean, single-stroke plottable Unicode expressions.
     */
    public String renderMathInText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 1. Process block math $$ ... $$ or \[ ... \]
        Pattern blockPattern = Pattern.compile("(\\$\\$|\\\\\\[)(.+?)(\\$\\$|\\\\\\])", Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (blockMatcher.find()) {
            String mathExpr = blockMatcher.group(2);
            String rendered = translateLatexMath(mathExpr);
            blockMatcher.appendReplacement(sb, Matcher.quoteReplacement(rendered));
        }
        blockMatcher.appendTail(sb);
        String step1 = sb.toString();

        // 2. Process inline math $ ... $ or \( ... \)
        Pattern inlinePattern = Pattern.compile("(\\$|\\\\\\()([^$\\n]+?)(\\$|\\\\\\))");
        Matcher inlineMatcher = inlinePattern.matcher(step1);
        StringBuilder sb2 = new StringBuilder();
        while (inlineMatcher.find()) {
            String mathExpr = inlineMatcher.group(2);
            String rendered = translateLatexMath(mathExpr);
            inlineMatcher.appendReplacement(sb2, Matcher.quoteReplacement(rendered));
        }
        inlineMatcher.appendTail(sb2);

        return sb2.toString();
    }

    /**
     * Translates a single raw LaTeX math expression into plottable Unicode.
     */
    public String translateLatexMath(String latex) {
        if (latex == null || latex.trim().isEmpty()) {
            return "";
        }

        String s = latex.trim();

        // Remove math formatting commands like \text{...}, \mathrm{...}, \mathbf{...}
        s = s.replaceAll("\\\\(text|mathrm|mathbf|mathit|textbf|textit)\\{([^}]+)\\}", "$2");

        // Normalize LaTeX spacing
        s = s.replaceAll("\\\\([,;:! ]|quad|qquad)", " ");

        // Normalize common LaTeX brackets & fences
        s = s.replaceAll("\\\\left\\[", "[");
        s = s.replaceAll("\\\\right\\]", "]");
        s = s.replaceAll("\\\\left\\(", "(");
        s = s.replaceAll("\\\\right\\)", ")");
        s = s.replaceAll("\\\\left\\\\\\{", "{");
        s = s.replaceAll("\\\\right\\\\\\}", "}");
        s = s.replaceAll("\\\\left\\|", "|");
        s = s.replaceAll("\\\\right\\|", "|");
        s = s.replaceAll("\\\\left\\.", "");
        s = s.replaceAll("\\\\right\\.", "");
        s = s.replaceAll("\\\\(Big|big|Bigg|bigg)[lr]?", "");

        // Escaped characters in LaTeX like \% or \$
        s = s.replaceAll("\\\\%", "%");
        s = s.replaceAll("\\\\\\$", "$");
        s = s.replaceAll("\\\\&", "&");
        s = s.replaceAll("\\\\_", "_");
        s = s.replaceAll("\\\\#", "#");
        s = s.replaceAll("\\\\\\{", "{");
        s = s.replaceAll("\\\\\\}", "}");

        // Replace Greek symbols (\alpha, \omega, \Omega, etc.) first
        for (Map.Entry<String, String> entry : GREEK_SYMBOLS.entrySet()) {
            s = s.replaceAll("\\\\" + entry.getKey() + "(?![a-zA-Z])", entry.getValue());
        }

        // Replace Math Operators (\int, \pm, \times, \approx, \infty, etc.)
        for (Map.Entry<String, String> entry : MATH_OPERATORS.entrySet()) {
            s = s.replaceAll("\\\\" + entry.getKey() + "(?![a-zA-Z])", entry.getValue());
        }

        // Replace Fractions recursively: \frac{num}{den} -> (num / den) or num/den
        s = parseFractions(s);

        // Replace Square Roots: \sqrt{x} -> √(x), \sqrt[n]{x} -> ⁿ√(x)
        s = parseRadicals(s);

        // Translate limits on operators like ∫_{a}^{b} or ∫_{a} or ∫^{b}
        s = parseIntegralAndLimits(s);

        // Replace Subscripts: _{...} or _char
        s = parseSubscripts(s);

        // Replace Superscripts: ^{...} or ^char
        s = parseSuperscripts(s);

        // Remove any stray unparsed backslashes before words
        s = s.replaceAll("\\\\([a-zA-Z]+)", "$1");

        // Clean up redundant consecutive spaces
        s = s.replaceAll("[ ]{2,}", " ").trim();

        return s;
    }

    private String parseFractions(String input) {
        Pattern fracPattern = Pattern.compile("\\\\(frac|dfrac|tfrac)\\{((?:[^{}]|\\{[^{}]*\\})*)\\}\\{((?:[^{}]|\\{[^{}]*\\})*)\\}");
        String current = input;
        Matcher m = fracPattern.matcher(current);
        while (m.find()) {
            String num = translateLatexMath(m.group(2));
            String den = translateLatexMath(m.group(3));

            String formatted;
            boolean numHasComplex = num.contains(" ") || num.contains("+") || num.contains("-");
            boolean denHasComplex = den.contains(" ") || den.contains("+") || den.contains("-");

            String numPart = numHasComplex ? "(" + num + ")" : num;
            String denPart = denHasComplex ? "(" + den + ")" : den;

            formatted = numPart + "/" + denPart;
            current = current.substring(0, m.start()) + formatted + current.substring(m.end());
            m = fracPattern.matcher(current);
        }
        return current;
    }

    private String parseRadicals(String input) {
        // \sqrt[n]{x}
        Pattern nthRootPattern = Pattern.compile("\\\\sqrt\\[([^\\]]+)\\]\\{([^}]+)\\}");
        Matcher m1 = nthRootPattern.matcher(input);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find()) {
            String n = toSuperscript(m1.group(1));
            String body = translateLatexMath(m1.group(2));
            m1.appendReplacement(sb1, Matcher.quoteReplacement(n + "√(" + body + ")"));
        }
        m1.appendTail(sb1);

        // \sqrt{x}
        Pattern sqrtPattern = Pattern.compile("\\\\sqrt\\{([^}]+)\\}");
        Matcher m2 = sqrtPattern.matcher(sb1.toString());
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            String body = translateLatexMath(m2.group(1));
            m2.appendReplacement(sb2, Matcher.quoteReplacement("√(" + body + ")"));
        }
        m2.appendTail(sb2);

        return sb2.toString();
    }

    private String parseIntegralAndLimits(String input) {
        // [∫∬∭∮∑∏]_{sub}^{sup} or [∫∬∭∮∑∏]^{sup}_{sub}
        Pattern intBoth = Pattern.compile("([∫∬∭∮∑∏])_\\{?([^{}\\s^]+)\\}?\\^\\{?([^{}\\s_]+)\\}?");
        Matcher m = intBoth.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String op = m.group(1);
            String sub = toSubscript(m.group(2));
            String sup = toSuperscript(m.group(3));
            m.appendReplacement(sb, Matcher.quoteReplacement(op + sub + sup));
        }
        m.appendTail(sb);

        // Reverse order: [∫∬∭∮∑∏]^{sup}_{sub}
        Pattern intBothRev = Pattern.compile("([∫∬∭∮∑∏])\\^\\{?([^{}\\s_]+)\\}?_\\{?([^{}\\s^]+)\\}?");
        Matcher mRev = intBothRev.matcher(sb.toString());
        StringBuffer sbRev = new StringBuffer();
        while (mRev.find()) {
            String op = mRev.group(1);
            String sup = toSuperscript(mRev.group(2));
            String sub = toSubscript(mRev.group(3));
            mRev.appendReplacement(sbRev, Matcher.quoteReplacement(op + sub + sup));
        }
        mRev.appendTail(sbRev);

        return sbRev.toString();
    }

    private String parseSubscripts(String input) {
        // Multi-char subscript: _{...}
        Pattern groupSub = Pattern.compile("_\\{([^}]+)\\}");
        Matcher m1 = groupSub.matcher(input);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find()) {
            String content = m1.group(1);
            m1.appendReplacement(sb1, Matcher.quoteReplacement(toSubscript(content)));
        }
        m1.appendTail(sb1);

        // Single-char subscript: _[0-9a-zA-Z+-]
        Pattern singleSub = Pattern.compile("_([0-9a-zA-Z+-])");
        Matcher m2 = singleSub.matcher(sb1.toString());
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            char ch = m2.group(1).charAt(0);
            Character sub = SUBSCRIPTS.get(ch);
            m2.appendReplacement(sb2, Matcher.quoteReplacement(sub != null ? String.valueOf(sub) : "_" + ch));
        }
        m2.appendTail(sb2);

        return sb2.toString();
    }

    private String parseSuperscripts(String input) {
        // Multi-char superscript: ^{...}
        Pattern groupSup = Pattern.compile("\\^\\{([^}]+)\\}");
        Matcher m1 = groupSup.matcher(input);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find()) {
            String content = m1.group(1);
            m1.appendReplacement(sb1, Matcher.quoteReplacement(toSuperscript(content)));
        }
        m1.appendTail(sb1);

        // Single-char superscript: ^[0-9a-zA-Z+-]
        Pattern singleSup = Pattern.compile("\\^([0-9a-zA-Z+-])");
        Matcher m2 = singleSup.matcher(sb1.toString());
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            char ch = m2.group(1).charAt(0);
            Character sup = SUPERSCRIPTS.get(ch);
            m2.appendReplacement(sb2, Matcher.quoteReplacement(sup != null ? String.valueOf(sup) : "^" + ch));
        }
        m2.appendTail(sb2);

        return sb2.toString();
    }

    public String toSuperscript(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : str.toCharArray()) {
            Character sup = SUPERSCRIPTS.get(ch);
            sb.append(sup != null ? sup : ch);
        }
        return sb.toString();
    }

    public String toSubscript(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : str.toCharArray()) {
            Character sub = SUBSCRIPTS.get(ch);
            sb.append(sub != null ? sub : ch);
        }
        return sb.toString();
    }
}
