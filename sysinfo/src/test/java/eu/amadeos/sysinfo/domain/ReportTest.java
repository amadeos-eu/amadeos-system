package eu.amadeos.sysinfo.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportTest {
    @Test
    void textAlignsTheValues() {
        Report report = new Report(List.of(
                new Report.Section("System", List.of(new Report.Item("Kernel", "6.12"), new Report.Item("Computer name", "amadeos"))),
                new Report.Section("Memory", List.of(new Report.Item("Memory", "2.0 GiB")))));
        assertEquals("""
                Info
                ====

                System
                ------
                Kernel:         6.12
                Computer name:  amadeos

                Memory
                ------
                Memory:         2.0 GiB
                """, report.toText("Info"));
    }
}
