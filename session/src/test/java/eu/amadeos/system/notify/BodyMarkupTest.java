package eu.amadeos.system.notify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BodyMarkupTest {
    @Test
    void keepsTheAllowedTags() {
        assertEquals("<b>bold</b> and <i>it</i> <u>u</u>", BodyMarkup.toHtml("<b>bold</b> and <i>it</i> <u>u</u>"));
        assertEquals("<a href=\"https://amadeos.eu\">site</a>", BodyMarkup.toHtml("<a href='https://amadeos.eu'>site</a>"));
    }

    @Test
    void escapesEverythingElse() {
        assertEquals("&lt;script&gt;x&lt;/script&gt;", BodyMarkup.toHtml("<script>x</script>"));
        assertEquals("a &lt; b &amp; c", BodyMarkup.toHtml("a < b & c"));
        assertEquals("1<br>2", BodyMarkup.toHtml("1\n2"));
        assertEquals("&amp; stays", BodyMarkup.toHtml("&amp; stays"));
    }

    @Test
    void dropsUnsafeLinksAndImages() {
        assertEquals("<a>x</a>", BodyMarkup.toHtml("<a href=\"file:///etc/passwd\">x</a>"));
        assertEquals("", BodyMarkup.toHtml("<img src=\"x.png\" alt=\"x\"/>"));
    }

    @Test
    void plainText() {
        assertEquals("bold & <more>\nline", BodyMarkup.plain("<b>bold</b> &amp; &lt;more&gt;<br>line"));
    }
}
