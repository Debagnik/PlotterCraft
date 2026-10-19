package in.debagnik.catplotlib.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MathExpressionParserService Unit Tests")
class MathExpressionParserServiceTest {

    private MathExpressionParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new MathExpressionParserService();
    }

    @Test
    @DisplayName("User exact definite integral LaTeX expression renders to authentic Unicode math")
    void testUserExactDefiniteIntegralExpression() {
        String input = "$A = \\int_{-2}^{2} (4 - x^2) \\, dx = \\left[ 4x - \\frac{x^3}{3} \\right]_{-2}^{2} = \\frac{32}{3} \\approx 10.67$";
        String rendered = parserService.renderMathInText(input);

        // Raw LaTeX tags should be converted
        assertThat(rendered).doesNotContain("$");
        assertThat(rendered).doesNotContain("\\int");
        assertThat(rendered).doesNotContain("\\frac");
        assertThat(rendered).doesNotContain("\\left");
        assertThat(rendered).doesNotContain("\\right");
        assertThat(rendered).doesNotContain("\\approx");
        assertThat(rendered).doesNotContain("\\,");

        // Authentic math symbols should be present
        assertThat(rendered).contains("∫");
        assertThat(rendered).contains("≈");
        assertThat(rendered).contains("²");
        assertThat(rendered).contains("₋₂");
        assertThat(rendered).contains("x³/3");
        assertThat(rendered).contains("32/3");
        assertThat(rendered).contains("10.67");
    }

    @Test
    @DisplayName("Physics resonant frequency with Greek symbols and square root fraction")
    void testPhysicsResonantFrequencyExpression() {
        String input = "Frequency $\\omega_0 = \\frac{1}{\\sqrt{LC}}$ rad/s  |  $f_0 = \\frac{\\omega_0}{2\\pi}$";
        String rendered = parserService.renderMathInText(input);

        assertThat(rendered).doesNotContain("$");
        assertThat(rendered).doesNotContain("\\omega");
        assertThat(rendered).doesNotContain("\\sqrt");
        assertThat(rendered).doesNotContain("\\frac");
        assertThat(rendered).doesNotContain("\\pi");

        assertThat(rendered).contains("ω₀");
        assertThat(rendered).contains("√(LC)");
        assertThat(rendered).contains("f₀");
        assertThat(rendered).contains("2π");
    }

    @Test
    @DisplayName("Greek alphabet translations")
    void testGreekAlphabet() {
        String input = "$\\alpha + \\beta + \\gamma + \\theta + \\lambda + \\mu + \\sigma + \\Omega + \\Delta$";
        String rendered = parserService.renderMathInText(input);

        assertThat(rendered).isEqualTo("α + β + γ + θ + λ + μ + σ + Ω + Δ");
    }

    @Test
    @DisplayName("Mathematical operators and relational symbols")
    void testMathOperators() {
        String input = "$x \\in [-2, 2], a \\pm b \\times c \\div d \\neq e \\leq f \\geq g \\approx h$";
        String rendered = parserService.renderMathInText(input);

        assertThat(rendered).contains("∈");
        assertThat(rendered).contains("±");
        assertThat(rendered).contains("×");
        assertThat(rendered).contains("÷");
        assertThat(rendered).contains("≠");
        assertThat(rendered).contains("≤");
        assertThat(rendered).contains("≥");
        assertThat(rendered).contains("≈");
    }

    @Test
    @DisplayName("Block math delimiters ($$ and \\[\\])")
    void testBlockMathDelimiters() {
        String input1 = "$$\\int_{0}^{\\infty} e^{-x} dx = 1$$";
        String rendered1 = parserService.renderMathInText(input1);
        assertThat(rendered1).contains("∫₀");
        assertThat(rendered1).contains("∞");
        assertThat(rendered1).contains("e⁻ˣ");

        String input2 = "\\[f(x) = x^2\\]";
        String rendered2 = parserService.renderMathInText(input2);
        assertThat(rendered2).isEqualTo("f(x) = x²");
    }

    @Test
    @DisplayName("Fractions with complex numerators and denominators")
    void testComplexFractions() {
        String input = "$\\frac{a + b}{c - d}$";
        String rendered = parserService.renderMathInText(input);
        assertThat(rendered).isEqualTo("(a + b)/(c - d)");
    }

    @Test
    @DisplayName("Subscript and superscript conversion helpers")
    void testSubscriptAndSuperscriptConversion() {
        assertThat(parserService.toSuperscript("0123456789+-ni")).isEqualTo("⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻ⁿⁱ");
        assertThat(parserService.toSubscript("0123456789+-aeoxij")).isEqualTo("₀₁₂₃₄₅₆₇₈₉₊₋ₐₑₒₓᵢⱼ");
    }

    @Test
    @DisplayName("Null and empty inputs are preserved safely")
    void testNullAndEmpty() {
        assertThat(parserService.renderMathInText(null)).isNull();
        assertThat(parserService.renderMathInText("")).isEmpty();
        assertThat(parserService.translateLatexMath(null)).isEmpty();
        assertThat(parserService.translateLatexMath("")).isEmpty();
        assertThat(parserService.toSuperscript(null)).isEmpty();
        assertThat(parserService.toSubscript(null)).isEmpty();
    }
}
