package com.example.glossariobuda;

/**
 * Quick test to check how WylieConverter handles spaces
 */
public class TestWylieSpaces {
    public static void main(String[] args) {
        System.out.println("Testing Wylie Converter with spaces:\n");

        String[] tests = {
            "gdung stegs kyi don byed nus pa",
            "a pha",
            "bka' drin che",
            "'a ba",
            "ka ba",
            "thugs rje che"
        };

        for (String wylie : tests) {
            String unicode = WylieConverter.toUnicode(wylie);
            System.out.println("Input:  " + wylie);
            System.out.println("Output: " + unicode);
            System.out.println("Hex:    " + toHex(unicode));
            System.out.println();
        }
    }

    private static String toHex(String s) {
        StringBuilder hex = new StringBuilder();
        for (char c : s.toCharArray()) {
            hex.append(String.format("U+%04X ", (int)c));
        }
        return hex.toString();
    }
}
