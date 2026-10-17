package org.datazuul.amadeos.print.service;

import org.datazuul.amadeos.commons.i18n.Messages;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextLayout;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.text.AttributedString;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Optional;

/** A one-page test print, drawn with Java2D and sent through the JDK's CUPS print service. */
public final class TestPage implements Printable {
    private final Messages msg;
    private final String printer;
    private final String user;
    private final ZonedDateTime when;

    public TestPage(Messages msg, String printer, String user, ZonedDateTime when) {
        this.msg = msg;
        this.printer = printer;
        this.user = user;
        this.when = when;
    }

    /** Prints the page on the CUPS queue of this name; false if the JDK does not know that printer. */
    public static boolean print(Messages msg, String printer, String user) throws PrintException {
        Optional<PrintService> service = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(s -> s.getName().equals(printer)).findFirst();
        if (service.isEmpty()) return false;
        DocPrintJob job = service.get().createPrintJob();
        HashPrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new JobName(msg.get("test.title"), msg.locale()));
        job.print(new SimpleDoc(new TestPage(msg, printer, user, ZonedDateTime.now()),
                DocFlavor.SERVICE_FORMATTED.PRINTABLE, null), attributes);
        return true;
    }

    @Override
    public int print(Graphics graphics, PageFormat page, int index) {
        if (index > 0) return NO_SUCH_PAGE;
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(page.getImageableX(), page.getImageableY());
        double w = page.getImageableWidth();
        double h = page.getImageableHeight();

        // A frame along the printable area shows whether the margins are right.
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(0, 0, (int) w - 1, (int) h - 1);

        int x = 36;
        float y = 60;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.drawString(msg.get("test.title"), x, y);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        y += 28;
        g.drawString(msg.format("test.printer", printer), x, y);
        y += 16;
        g.drawString(msg.format("test.user", user), x, y);
        y += 16;
        g.drawString(msg.format("test.date", when.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(msg.locale()))), x, y);
        y += 28;
        AttributedString text = new AttributedString(msg.get("test.text"));
        text.addAttribute(java.awt.font.TextAttribute.FONT, g.getFont());
        LineBreakMeasurer lines = new LineBreakMeasurer(text.getIterator(), g.getFontRenderContext());
        while (lines.getPosition() < msg.get("test.text").length()) {
            TextLayout line = lines.nextLayout((float) w - 2 * x);
            y += line.getAscent();
            line.draw(g, x, y);
            y += line.getDescent() + line.getLeading();
        }

        // Colour bars and a grey ramp.
        y += 24;
        Color[] colors = {Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE};
        int bar = (int) ((w - 2 * x) / colors.length);
        for (int i = 0; i < colors.length; i++) {
            g.setColor(colors[i]);
            g.fillRect(x + i * bar, (int) y, bar - 4, 40);
        }
        y += 52;
        for (int i = 0; i < 10; i++) {
            int v = 255 - i * 255 / 9;
            g.setColor(new Color(v, v, v));
            g.fillRect(x + i * (int) ((w - 2 * x) / 10), (int) y, (int) ((w - 2 * x) / 10), 24);
        }
        g.setColor(Color.BLACK);
        g.drawRect(x, (int) y, (int) (w - 2 * x), 24);
        return PAGE_EXISTS;
    }
}
