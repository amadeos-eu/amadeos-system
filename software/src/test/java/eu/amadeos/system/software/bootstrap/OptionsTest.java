package eu.amadeos.system.software.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionsTest {
    @Test
    void searchOption() {
        assertEquals(Optional.of("fonts-"), AmadeSoftwareApplication.search(List.of("--search=fonts-")));
        assertEquals(Optional.of("b"), AmadeSoftwareApplication.search(List.of("--search=a", "--updates", "--search=b")));
        assertEquals(Optional.empty(), AmadeSoftwareApplication.search(List.of("--search=  ")));
        assertEquals(Optional.empty(), AmadeSoftwareApplication.search(List.of("--updates")));
    }
}
