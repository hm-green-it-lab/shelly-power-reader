package edu.hm.greenit.tools.shelly;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Optional;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
@CommandLine.Command(name = "shelly-power-reader",
        description = "Read power values from Shelly devices")
@QuarkusMain
public class ShellyPowerReader implements QuarkusApplication {
    public static final String SHELLY_USER = "admin"; //always admin, see: https://shelly-api-docs.shelly.cloud/gen2/General/Authentication
    private static final String SHELLY_GEN1_ARG = "1";
    private static final String SHELLY_GEN2PLUS_ARG = "2+";
    private static final Logger LOGGER = Logger.getLogger(ShellyPowerReader.class);
    private static boolean initialized = false;

    private IReader reader;

    @CommandLine.Option(names = {"--ip", "-i"}, description = "IP address of the shelly device", required = true)
    private String shellyIp;

    @CommandLine.Option(names = {"--password", "-p"}, description = "Password of the shelly device")
    private String shellyPassword;

    @CommandLine.Option(names = {"--generation", "-g"},
            description = "The generation of the shelly device, one of: [" + SHELLY_GEN1_ARG + ", " + SHELLY_GEN2PLUS_ARG + "]",
            defaultValue = SHELLY_GEN2PLUS_ARG)
    private String shellyGeneration;

    /**
     * @param args ip address, password (if set) and generation of the Shelly device (1 or 2+).
     *             The Generation determines the protocol that is used. Gen1 uses Http API, Gen2+ uses RPC. Default is Gen2+.
     * @return - status of the execution
     */
    @Override
    public int run(String... args) throws InterruptedException {
        CommandLine commandLine = new CommandLine(this);
        try {
            CommandLine.ParseResult parseResult = commandLine.parseArgs(args);
            if (parseResult.isUsageHelpRequested()) {
                LOGGER.info(commandLine.getUsageMessage());
                return 0;
            }
        } catch (CommandLine.ParameterException e) {
            LOGGER.error("Error parsing command line arguments", e);
            LOGGER.info(commandLine.getUsageMessage());
            return 1;
        }

        reader = switch (shellyGeneration) {
            case SHELLY_GEN1_ARG -> new HttpApiReader(shellyIp, shellyPassword);
            case SHELLY_GEN2PLUS_ARG -> new RpcReader(shellyIp, shellyPassword);
            default -> throw new IllegalStateException("Unrecognized shelly generation: " + shellyGeneration);
        };
        initialized = true;

        // print header
        System.out.print("ip");
        System.out.print(",");
        System.out.print("timestamp");
        System.out.print(",");
        System.out.print("power");
        System.out.print(",");
        System.out.println("energy");

        while (true) {
            // keep application up;
            Thread.sleep(60000);
        }

    }

    public static void main(String[] args) {
        Quarkus.run(ShellyPowerReader.class, args);
    }

    @RunOnVirtualThread
    @Scheduled(every = "1s", concurrentExecution = SKIP)
    void readShellyDataAndPrintResults() throws IOException, InterruptedException {
        if (!initialized) {
            LOGGER.debug("Running scheduler, reader not initialized");
            return;
        }

        Optional<Meter> meter = reader.readPowerConsumption();
        meter.ifPresent(this::printPowerConsumption);
    }

    void printPowerConsumption(final Meter meter) {
        System.out.print(shellyIp);
        System.out.print(",");
        System.out.print(meter.getTimestamp());
        System.out.print(",");
        System.out.print(meter.getPower());
        System.out.print(",");
        System.out.println(meter.getTotal());
    }

}