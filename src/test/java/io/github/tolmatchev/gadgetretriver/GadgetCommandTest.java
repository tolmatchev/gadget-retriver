package io.github.tolmatchev.gadgetretriver;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tolmatchev.gadgetretriver.cli.GadgetCommand;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class GadgetCommandTest {
  @Test
  void commandSucceedsAndExposesHelpAndVersion() {
    GadgetCommand callable = new GadgetCommand();
    assertThat(callable.call()).isZero();
    CommandLine command = new CommandLine(callable);
    StringWriter output = new StringWriter();
    command.setOut(new PrintWriter(output));
    command.setErr(new PrintWriter(output));
    assertThat(command.execute()).isZero();
    assertThat(command.execute("--help")).isZero();
    assertThat(output.toString()).contains("Usage: gadget-retriver", "--version");
    output.getBuffer().setLength(0);
    assertThat(command.execute("--version")).isZero();
    assertThat(output.toString()).contains("0.1.0-SNAPSHOT");
    assertThat(command.execute("--unknown")).isEqualTo(2);
  }
}
