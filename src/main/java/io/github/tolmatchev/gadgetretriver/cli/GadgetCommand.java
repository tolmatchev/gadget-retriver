package io.github.tolmatchev.gadgetretriver.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/** Initial command surface; data ingestion is implemented separately. */
@Command(
    name = "gadget-retriver",
    mixinStandardHelpOptions = true,
    version = "0.1.0-SNAPSHOT",
    description = "A versioned catalog mapping device identifiers to marketing names.")
public final class GadgetCommand implements Callable<Integer> {
  @Override
  public Integer call() {
    return 0;
  }
}
