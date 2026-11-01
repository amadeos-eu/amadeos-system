package eu.amadeos.system.taskmanager.ui;

import eu.amadeos.system.taskmanager.model.ProgramRow;
import eu.amadeos.system.taskmanager.model.Sample;
import eu.amadeos.system.taskmanager.x.WindowInfo;

import java.util.List;

/** One update of the window: the sample, the program windows and the workspace names. */
public record Snapshot(Sample sample, List<ProgramRow> programs, List<WindowInfo> windows,
                       List<String> workspaces) {
}
