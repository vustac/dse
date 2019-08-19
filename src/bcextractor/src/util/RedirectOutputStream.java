/*
 * This class extends from OutputStream to redirect output to a JTextArrea.
 */
package util;

//package net.codejava.swing;

import java.io.IOException;
import java.io.OutputStream;
import javax.swing.JTextArea;

/**
 * This class extends from OutputStream to redirect output to a JTextArrea.
 * @author dmcd2356
 */
public class RedirectOutputStream extends OutputStream {
    private final JTextArea textArea;

    /**
     *
     * @param textArea - the text area to use for redirecting command responses to
     */
    public RedirectOutputStream(JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void write(int b) throws IOException {
        // redirects data to the text area
        textArea.append(String.valueOf((char)b));
        // scrolls the text area to the end of data
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }
}
