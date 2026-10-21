package eu.amadeos.system.software.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlTest {
    @Test
    void debianDescriptionsBecomeParagraphsListsAndPreformattedText() {
        String description = """
                Vim is an almost compatible version of the UNIX editor Vi.
                Many new features have been added.

                Features:
                 * multiple windows & buffers
                 * syntax highlighting,
                   for many languages

                  $ vim file.txt
                  :wq""";
        assertEquals("<p>Vim is an almost compatible version of the UNIX editor Vi. Many new features have been added.</p>"
                + "<p>Features:</p><ul><li>multiple windows &amp; buffers</li>"
                + "<li>syntax highlighting, for many languages</li></ul>"
                + "<pre>  $ vim file.txt\n  :wq</pre>", Html.fromDebian(description));
        assertEquals("", Html.fromDebian(""));
    }

    @Test
    void appStreamMarkupKeepsOnlyItsOwnElements() {
        assertEquals("<p>GIMP is <em>free</em>.</p><ul><li>one</li><li>two</li></ul>",
                Html.fromAppStream("<p>GIMP is <em>free</em>.</p><ul><li>one</li><li>two</li></ul>"));
        assertEquals("<p>click me</p>", Html.fromAppStream("<p><a href='https://x'>click</a> <script>me</script></p>"));
        assertEquals("<p>a &lt; b &amp; c</p>", Html.fromAppStream("a < b & c"));
        assertEquals("<p>&lt;!DOCTYPE x [&lt;!ENTITY e SYSTEM 'file:///etc/passwd'&gt;]&gt;&lt;p&gt;&amp;e;&lt;/p&gt;</p>",
                Html.fromAppStream("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><p>&e;</p>"));
    }
}
