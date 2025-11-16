package com.example.glossariobuda;

import com.ibm.icu.text.Transliterator;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Integrated transliteration test controller
 * Uses real converters (Slp1Converter + ICU4J) to test SLP1 → IAST → Devanāgarī → Latin
 */
public class TransliterationTestController {

    @FXML private TextField inputField;       // SLP1 input
    @FXML private TextField iastField;        // IAST output
    @FXML private TextField devanagariField;  // Devanāgarī output
    @FXML private TextField latinField;       // Latin re-transliteration
    @FXML private TextArea resultArea;        // Output log

    // ICU4J Transliterator instances
    private static final Transliterator LATIN_TO_DEVANAGARI;
    private static final Transliterator DEVANAGARI_TO_LATIN;

    static {
        Transliterator tLatinToDeva = null;
        Transliterator tDevaToLatin = null;
        try {
            // Force-load parser class to ensure custom transliterator registration
            Class.forName("com.example.glossariobuda.MW72Parser");

            // Retrieve the registered transliterator by ID (not by variable)
            tLatinToDeva = MW72Parser.getCustomTransliterator();
            System.out.println("[DEBUG] Using custom transliterator: " + tLatinToDeva.getID());

            // Default Devanāgarī → Latin (back conversion)
            tDevaToLatin = Transliterator.getInstance("Devanagari-Latin");
        } catch (Exception e) {
            System.err.println("[DEBUG] Falling back to default ICU transliterators: " + e.getMessage());
            tLatinToDeva = MW72Parser.getCustomTransliterator();
            tDevaToLatin = Transliterator.getInstance("Devanagari-Latin");
        }

        LATIN_TO_DEVANAGARI = tLatinToDeva;
        DEVANAGARI_TO_LATIN = tDevaToLatin;
    }


    /** Convert SLP1 → IAST */
    @FXML
    private void convertSlp1ToIast() {
        try {
            String slp1 = inputField.getText().trim();
            if (slp1.isEmpty()) {
                resultArea.appendText("⚠️ Please enter SLP1 text first.\n");
                return;
            }

            // ✅ Use your actual Slp1Converter
            String iast = Slp1Converter.convert(slp1);
            iastField.setText(iast);
            resultArea.appendText("SLP1 → IAST: " + slp1 + " → " + iast + "\n");

        } catch (Exception e) {
            resultArea.appendText("❌ Error converting SLP1 → IAST: " + e.getMessage() + "\n");
        }
    }

    /** Convert IAST → Devanāgarī */
    @FXML
    private void convertIastToDevanagari() {
        try {
            String iast = iastField.getText().trim();
            if (iast.isEmpty()) iast = inputField.getText().trim();

            String devanagari = LATIN_TO_DEVANAGARI.transliterate(iast);
            devanagariField.setText(devanagari);
            resultArea.appendText("IAST → Devanāgarī: " + iast + " → " + devanagari + "\n");

        } catch (Exception e) {
            resultArea.appendText("❌ Error converting IAST → Devanāgarī: " + e.getMessage() + "\n");
        }
    }

    /** Convert Devanāgarī → Latin (reverse test) */
    @FXML
    private void convertDevanagariToLatin() {
        try {
            String devanagari = devanagariField.getText().trim();
            if (devanagari.isEmpty()) {
                resultArea.appendText("⚠️ No Devanāgarī text to convert.\n");
                return;
            }

            String latin = DEVANAGARI_TO_LATIN.transliterate(devanagari);
            latinField.setText(latin);
            resultArea.appendText("Devanāgarī → Latin: " + devanagari + " → " + latin + "\n");

        } catch (Exception e) {
            resultArea.appendText("❌ Error converting Devanāgarī → Latin: " + e.getMessage() + "\n");
        }
    }

    /** Run all conversions sequentially (pipeline test) */
    @FXML
    private void runFullTest() {
        convertSlp1ToIast();
        convertIastToDevanagari();
        convertDevanagariToLatin();
        resultArea.appendText("✅ Full transliteration pipeline complete.\n\n");
    }
}
