package eu.amadeos.system.printers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LpadminTest {

    @Test
    void devicesFoundByLpinfo() {
        String out = """
                Device: uri = dnssd://Brother%20HL-L2350DW%20series._ipp._tcp.local/?uuid=e3248000-80ce-11db-8000-3c2af4a2b1c0
                        class = network
                        info = Brother HL-L2350DW series
                        make-and-model = Brother HL-L2350DW series
                        device-id = MFG:Brother;CMD:PJL,PCL,PCLXL,URF;MDL:HL-L2350DW series;
                        location = Office
                Device: uri = dnssd://EPSON%20ET-2810%20Series._ipp._tcp.local/
                        class = network
                        info = EPSON ET-2810 Series
                        make-and-model = EPSON ET-2810 Series
                        device-id =
                        location =
                """;
        List<FoundPrinter> found = Lpadmin.parseDevices(out);
        assertEquals(2, found.size());
        assertEquals("Brother HL-L2350DW series", found.get(0).info());
        assertEquals("Office", found.get(0).location());
        assertTrue(found.get(0).uri().startsWith("dnssd://Brother%20HL-L2350DW"));
        assertEquals("", found.get(1).location());
        assertEquals("Brother_HL-L2350DW_series", found.get(0).queueName());
        assertEquals(List.of(), Lpadmin.parseDevices(""));
    }

    @Test
    void paperAndDuplexFromLpoptions() {
        String out = """
                PageSize/Media Size: Letter Legal Executive *A4 A5 Env10
                InputSlot/Media Source: *Auto Tray1 Manual
                Duplex/2-Sided Printing: None *DuplexNoTumble DuplexTumble
                ColorModel/Output Mode: *Gray
                """;
        PrinterOptions o = Lpadmin.parseOptions(out);
        assertEquals(List.of("Letter", "Legal", "Executive", "A4", "A5", "Env10"), o.pageSizes());
        assertEquals("A4", o.pageSize());
        assertEquals("DuplexNoTumble", o.duplex());
        assertTrue(o.canDuplex());
        assertFalse(Lpadmin.parseOptions("PageSize/Media Size: *A4\n").canDuplex());
    }

    @Test
    void commandLines() {
        assertEquals(List.of("lpadmin", "-p", "Office", "-E", "-v", "ipp://10.0.0.5/ipp/print", "-m", "everywhere",
                "-D", "Office printer", "-L", "Hall"), Lpadmin.add("Office", "ipp://10.0.0.5/ipp/print", "Office printer", "Hall"));
        assertEquals(List.of("lpadmin", "-p", "Office", "-o", "PageSize-default=A4", "-o", "Duplex-default=None"),
                Lpadmin.setOptions("Office", "A4", "None"));
        assertEquals(List.of("lp", "-d", "Office", "-t", "Test page", Lpadmin.TEST_PAGE), Lpadmin.testPage("Office", "Test page"));
        assertEquals("Printer", FoundPrinter.queueName(" ** "));
        assertEquals("HP_OfficeJet_Pro_9010", FoundPrinter.queueName("HP OfficeJet Pro 9010"));
    }
}
