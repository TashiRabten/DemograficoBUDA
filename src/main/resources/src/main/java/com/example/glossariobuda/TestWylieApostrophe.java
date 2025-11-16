package com.example.glossariobuda;

/**
 * Test to debug apostrophe conversion issue
 */
public class TestWylieApostrophe {
    public static void main(String[] args) {
        System.out.println("Testing Wylie apostrophe conversion:\n");

        String[] tests = {
            "bod yig gsal byed dang po 'di'i nga ro 'don tshul",
            "'di'i",
            "'don",
            "phyi'i",
            "ming gzhi'i pho yig"
        };

        for (String wylie : tests) {
            String unicode = WylieConverter.toUnicode(wylie);
            System.out.println("Input:    \"" + wylie + "\"");
            System.out.println("Output:   \"" + unicode + "\"");
            System.out.println("Expected: " + getExpected(wylie));
            System.out.println();
        }
    }

    private static String getExpected(String wylie) {
        switch(wylie) {
            case "'di'i": return "འདིའི";
            case "'don": return "འདོན";
            case "phyi'i": return "ཕྱིའི";
            case "ming gzhi'i pho yig": return "མིང་གཞིའི་ཕོ་ཡིག";
            default: return "(check manually)";
        }
    }
}
