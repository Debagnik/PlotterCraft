package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.model.VectorStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AWT Font Fallback Diagnostic Test")
class AwtFontFallbackTest {

    @Test
    @DisplayName("Verify Java AWT can resolve Devanagari and Japanese outlines")
    void testAwtGlyphVectorGeneration() {
        String[] fonts = {"Nirmala UI", "MS Gothic", "Yu Gothic", "Segoe UI", Font.DIALOG, Font.SANS_SERIF};
        char devanagari = 'क';
        char hiragana = 'あ';
        char kanji = '字';

        Font foundDevanagari = null;
        Font foundHiragana = null;
        Font foundKanji = null;

        for (String name : fonts) {
            Font f = new Font(name, Font.PLAIN, 16);
            if (foundDevanagari == null && f.canDisplay(devanagari)) {
                foundDevanagari = f;
            }
            if (foundHiragana == null && f.canDisplay(hiragana)) {
                foundHiragana = f;
            }
            if (foundKanji == null && f.canDisplay(kanji)) {
                foundKanji = f;
            }
        }

        System.out.println("Devanagari font: " + (foundDevanagari != null ? foundDevanagari.getFontName() : "none"));
        System.out.println("Hiragana font: " + (foundHiragana != null ? foundHiragana.getFontName() : "none"));
        System.out.println("Kanji font: " + (foundKanji != null ? foundKanji.getFontName() : "none"));

        assertThat(foundHiragana).isNotNull();
        assertThat(foundKanji).isNotNull();
        assertThat(foundDevanagari).isNotNull();

        // Check outline generation for Kanji
        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = foundKanji.createGlyphVector(frc, String.valueOf(kanji));
        Shape outline = gv.getOutline(0, 0);
        PathIterator pi = outline.getPathIterator(null, 0.4);

        int pointCount = 0;
        float[] coords = new float[6];
        while (!pi.isDone()) {
            pi.currentSegment(coords);
            pointCount++;
            pi.next();
        }

        System.out.println("Kanji outline points: " + pointCount);
        assertThat(pointCount).isGreaterThan(5);
    }
}
