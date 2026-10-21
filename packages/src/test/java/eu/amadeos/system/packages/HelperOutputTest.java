package eu.amadeos.system.packages;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HelperOutputTest {

    @Test
    void statusLinesRecordedFromAptBecomeProgress() {
        List<Progress> progress = Fixtures.read("status-fd.txt").lines().map(HelperOutput::parse)
                .map(line -> assertInstanceOf(HelperOutput.Status.class, line).progress()).toList();
        Progress download = progress.get(5);
        assertEquals(new Progress(Progress.Phase.DOWNLOAD, 80, "1", "Retrieving file 1 of 1"), download);
        assertEquals(26, download.overall());

        Progress installed = progress.getLast();
        assertEquals(new Progress(Progress.Phase.INSTALL, 80, "distro-info-data", "Installed distro-info-data (amd64)"),
                installed);
        assertEquals(86, installed.overall());
        assertEquals(33, progress.get(7).overall(), "installing starts after the download's third");
    }

    @Test
    void messagesMayContainColons() {
        HelperOutput.Line line = HelperOutput.parse("pmerror:/var/cache/apt/archives/x.deb:50.0000:trying: to overwrite");
        assertEquals(new HelperOutput.Failure("/var/cache/apt/archives/x.deb", "trying: to overwrite"), line);
    }

    @Test
    void helperLines() {
        assertEquals(new HelperOutput.Done(), HelperOutput.parse("@@done"));
        assertEquals(new HelperOutput.Nothing(), HelperOutput.parse("@@nothing"));
        assertEquals(new HelperOutput.RebootRequired(), HelperOutput.parse("@@reboot-required"));
        assertEquals(new HelperOutput.Error("locked", "another package operation is running"),
                HelperOutput.parse("@@error locked another package operation is running"));
        assertEquals(new HelperOutput.Text("Inst foo [1] (2 Debian:13/stable [all])"),
                HelperOutput.parse("Inst foo [1] (2 Debian:13/stable [all])"));
        assertEquals(new HelperOutput.Text("@@percent 5"), HelperOutput.parse("@@percent 5"));
    }
}
