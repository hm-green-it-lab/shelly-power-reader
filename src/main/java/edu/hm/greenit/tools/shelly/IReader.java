package edu.hm.greenit.tools.shelly;

import java.io.IOException;
import java.util.Optional;

public interface IReader {
    Optional<Meter> readPowerConsumption() throws IOException, InterruptedException;
}
