package eu.amadeos.system.screen.modules;

import eu.amadeos.system.screen.ScreenSettings;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The available screen saver modules. New instances are created for every use, since modules keep state.
 */
public final class SaverModules {

    public record Entry(String id, String name, Supplier<SaverModule> factory) {
        @Override
        public String toString() {
            return name;
        }
    }

    public static final List<Entry> ALL = List.of(
            new Entry("blank", "Blank", BlankModule::new),
            new Entry("qix", "Qix", QixModule::new),
            new Entry("swarm", "Swarm", SwarmModule::new),
            new Entry("starfield", "Starfield", StarfieldModule::new),
            new Entry("logo", "Floating Logo", LogoModule::new),
            new Entry("lava", "Lava Lamp", LavaLampModule::new));

    /** Id of the lava lamp, the module with a choice of colours. */
    public static final String LAVA = "lava";

    private SaverModules() {
    }

    /** The module with this id, a random one for {@link ScreenSettings#RANDOM}, Qix for unknown ids. */
    public static SaverModule create(String id, Random random) {
        return create(id, LavaColors.DEFAULT, random);
    }

    /**
     * The module with this id, as {@link #create(String, Random)}; the lava lamp gets the colours with the
     * id {@code lavaColors} (see {@link LavaColors}).
     */
    public static SaverModule create(String id, String lavaColors, Random random) {
        Entry entry;
        if (ScreenSettings.RANDOM.equals(id)) {
            // Blank is not an animation; pick among the others.
            entry = ALL.get(1 + random.nextInt(ALL.size() - 1));
        } else {
            entry = ALL.stream().filter(e -> e.id().equals(id)).findFirst().orElse(ALL.get(1));
        }
        if (entry.id().equals(LAVA)) return new LavaLampModule(LavaColors.byId(lavaColors));
        return entry.factory().get();
    }
}
