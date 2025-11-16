package com.example.glossariobuda;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.io.InputStream;

/**
 * SWT Dialog for Tibetan text input with proper IME support.
 *
 * This dialog provides a text area where the Tibetan keyboard's subscript
 * modifier 'a' works correctly, unlike JavaFX TextField/TextArea.
 *
 * Usage:
 *   String result = TibetanInputDialog.open(parentShell, "Initial text", false);
 */
public class TibetanInputDialog {

    private Shell shell;
    private Text textArea;
    private String resultText = null;
    private boolean multiline;
    private String initialText;

    /**
     * Opens a Tibetan input dialog.
     *
     * @param parent Parent shell (can be null for standalone)
     * @param initialText Initial text to display (can be null or empty)
     * @param multiline true for multi-line input, false for single line
     * @return The entered text, or null if cancelled
     */
    public static String open(Shell parent, String initialText, boolean multiline) {
        TibetanInputDialog dialog = new TibetanInputDialog(parent, initialText, multiline);
        dialog.open();
        return dialog.resultText;
    }

    private TibetanInputDialog(Shell parent, String initialText, boolean multiline) {
        this.shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.ON_TOP);
        this.initialText = initialText != null ? initialText : "";
        this.multiline = multiline;
    }

    private void open() {
        createContents();

        // Set application icon
        try {
            InputStream iconStream = getClass().getResourceAsStream("/icons/pramana.png");
            if (iconStream != null) {
                Image icon = new Image(shell.getDisplay(), iconStream);
                shell.setImage(icon);
                shell.addListener(SWT.Dispose, event -> {
                    if (!icon.isDisposed()) {
                        icon.dispose();
                    }
                });
                iconStream.close();
            }
        } catch (Exception e) {
            System.err.println("[Tibetan Input] Could not load icon: " + e.getMessage());
        }

        shell.pack();
        shell.setSize(800, multiline ? 600 : 400);

        // Center on screen
        Monitor primary = shell.getDisplay().getPrimaryMonitor();
        org.eclipse.swt.graphics.Rectangle bounds = primary.getBounds();
        org.eclipse.swt.graphics.Rectangle rect = shell.getBounds();
        int x = bounds.x + (bounds.width - rect.width) / 2;
        int y = bounds.y + (bounds.height - rect.height) / 2;
        shell.setLocation(x, y);

        shell.open();

        Display display = shell.getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    private void createContents() {
        shell.setText("Entrada de Texto Tibetano");
        shell.setLayout(new GridLayout(1, false));

        // Instructions in Portuguese - short and simple
        Label instructions = new Label(shell, SWT.WRAP);
        instructions.setText("Digite o texto tibetano:");
        GridData instructionsData = new GridData(SWT.FILL, SWT.TOP, true, false);
        instructions.setLayoutData(instructionsData);

        // Text input area
        int style = SWT.BORDER;
        if (multiline) {
            style |= SWT.MULTI | SWT.WRAP | SWT.V_SCROLL;
        } else {
            style |= SWT.SINGLE;
        }

        textArea = new Text(shell, style);
        GridData textData = new GridData(SWT.FILL, SWT.FILL, true, true);
        textData.heightHint = multiline ? 200 : 30;
        textArea.setLayoutData(textData);

        // Set initial text
        if (!initialText.isEmpty()) {
            textArea.setText(initialText);
            textArea.setSelection(initialText.length()); // Cursor at end
        }

        // Try multiple Tibetan fonts in order of preference
        Font tibetanFont = createTibetanFont(shell.getDisplay());
        if (tibetanFont != null) {
            textArea.setFont(tibetanFont);

            // Dispose font when shell closes
            shell.addListener(SWT.Dispose, event -> {
                if (!tibetanFont.isDisposed()) {
                    tibetanFont.dispose();
                }
            });
        }

        // Preview label to show text is captured even if font rendering fails
        Label previewLabel = new Label(shell, SWT.WRAP | SWT.BORDER);
       // previewLabel.setText("Pré-visualização: (atualiza enquanto você digita)");
        GridData previewData = new GridData(SWT.FILL, SWT.TOP, true, false);
        previewData.heightHint = 100; // Make preview bigger
        previewLabel.setLayoutData(previewData);

        // Set larger font for preview
        Font previewFont = new Font(shell.getDisplay(), "Arial", 14, SWT.NORMAL);
        previewLabel.setFont(previewFont);
        shell.addListener(SWT.Dispose, event -> {
            if (!previewFont.isDisposed()) {
                previewFont.dispose();
            }
        });

        // Update preview as user types
        textArea.addModifyListener(e -> {
            String text = textArea.getText();
            boolean hasTibetan = text.chars().anyMatch(c -> (c >= 0x0F00 && c <= 0x0FFF));
          //  previewLabel.setText(
                //"Pré-visualização: " + text + "\n" +
               // "Unicode Tibetano: " + (hasTibetan ? "✓ Sim" : "✗ Não") +
               // " | Caracteres: " + text.length()
           // );
        });

        // Buttons
        Composite buttonBar = new Composite(shell, SWT.NONE);
        buttonBar.setLayout(new GridLayout(2, true));
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        Button okButton = new Button(buttonBar, SWT.PUSH);
        okButton.setText("OK");
        okButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        // Blue background for OK button
        okButton.setBackground(shell.getDisplay().getSystemColor(SWT.COLOR_BLUE));
        okButton.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        okButton.addListener(SWT.Selection, e -> {
            resultText = textArea.getText();
            shell.dispose();
        });

        Button cancelButton = new Button(buttonBar, SWT.PUSH);
        cancelButton.setText("Cancelar");
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cancelButton.addListener(SWT.Selection, e -> {
            resultText = null;
            shell.dispose();
        });

        // Set OK as default button (Enter key)
        shell.setDefaultButton(okButton);

        // Focus on text area
        textArea.setFocus();
    }

    /**
     * Creates a Tibetan font, trying multiple font names in order of preference.
     */
    private Font createTibetanFont(Display display) {
        String[] fontNames = {
            "Microsoft Himalaya",
            "Jomolhari",
            "Tibetan Machine Uni",
            "DDC Uchen",
            "Monlam Uni Ouchan2",
            "Noto Sans Tibetan",
            "Arial Unicode MS"
        };

        int fontSize = 30; // Bigger font for Tibetan text input

        // Try each font
        for (String fontName : fontNames) {
            try {
                Font font = new Font(display, fontName, fontSize, SWT.NORMAL);
                FontData[] fontData = font.getFontData();

                // Check if the font was actually found (some systems return default font)
                if (fontData.length > 0 && fontData[0].getName().equalsIgnoreCase(fontName)) {
                    System.out.println("[Tibetan Input] Using font: " + fontName);
                    return font;
                }
                font.dispose();
            } catch (Exception e) {
                // Font not available, try next
            }
        }

        // Fallback to default font with larger size
        System.out.println("[Tibetan Input] No Tibetan font found, using default");
        return new Font(display, "Arial", fontSize, SWT.NORMAL);
    }
}
