// File: SimulationRunner.java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SimulationRunner {

    private static class NodeInitialConfig {
        final int id; final double x; final double y; final double initialEnergy;
        NodeInitialConfig(int id, double x, double y, double initialEnergy) {
            this.id = id; this.x = x; this.y = y; this.initialEnergy = initialEnergy;
        }
    }

    public static void main(String[] args) {
        PEGASISConfig.parseArgs(args);
        List<NodeInitialConfig> initialNodeDeployments = generateInitialNodeDeployments();

        Protocol standardPegasis = new StandardPegasisProtocol();
        runProtocolSimulation(standardPegasis, initialNodeDeployments, "StandardPegasisRun");

        System.out.println(); System.out.println(); // Add some spacing
        System.out.println("=======================================================");
        System.out.println("DEBUG: Preparing to run HALEM-PEGASIS...");
        Protocol halemPegasis = new HALEMPegasisProtocol();
        runProtocolSimulation(halemPegasis, initialNodeDeployments, "HALEMPegasisRun");
        System.out.println("DEBUG: HALEM-PEGASIS run attempt completed.");
        System.out.println("=======================================================");

        System.out.println(); System.out.println();
        System.out.println("All configured simulations complete.");
    }

    private static List<NodeInitialConfig> generateInitialNodeDeployments() {
        List<NodeInitialConfig> configs = new ArrayList<>();
        Random deploymentRandom = new Random(42);
        System.out.println(); // Spacing
        System.out.println("Generating initial node deployment...");
        for (int i = 0; i < PEGASISConfig.NUM_NODES; i++) {
            double x = deploymentRandom.nextDouble() * PEGASISConfig.AREA_WIDTH;
            double y = deploymentRandom.nextDouble() * PEGASISConfig.AREA_HEIGHT;
            configs.add(new NodeInitialConfig(i + 1, x, y, PEGASISConfig.INITIAL_ENERGY));
        }
        System.out.println(PEGASISConfig.NUM_NODES + " node configurations generated.");
        return configs;
    }

    private static List<SensorNode> createNodesFromInitialConfig(List<NodeInitialConfig> initialConfigs) {
        List<SensorNode> nodes = new ArrayList<>();
        for (NodeInitialConfig config : initialConfigs) {
            nodes.add(new SensorNode(config.id, config.x, config.y, config.initialEnergy));
        }
        return nodes;
    }

    private static void runProtocolSimulation(Protocol protocol, List<NodeInitialConfig> initialNodeDeployments, String runSuffix) {
        System.out.println(); System.out.println();
        System.out.println("=======================================================");
        System.out.println("Starting Simulation for: " + protocol.getProtocolName() + " (Run: " + runSuffix + ")");
        System.out.println("=======================================================");

        BaseStation sink = new BaseStation(0, PEGASISConfig.BS_X, PEGASISConfig.BS_Y);
        sink.reset();
        List<SensorNode> currentNodes = createNodesFromInitialConfig(initialNodeDeployments);
        MetricsCollector metrics = new MetricsCollector(PEGASISConfig.NUM_NODES);
        Random protocolSpecificRandom = new Random(protocol.getProtocolName().hashCode() + runSuffix.hashCode());
        protocol.setup(currentNodes, sink, metrics, protocolSpecificRandom);

        int currentRound = 0;
        for (currentRound = 1; currentRound <= PEGASISConfig.MAX_ROUNDS; currentRound++) {
            long roundStartTimeSystemNanos = System.nanoTime();
            if (PEGASISConfig.IS_SINK_MOBILE) {
                sink.move();
            }
            Map<String, Object> roundStats = protocol.runSingleRound(currentRound);
            metrics.addRoundExecutionTime(System.nanoTime() - roundStartTimeSystemNanos);

            if (currentRound % 100 == 0 || currentRound == 1 || currentRound == PEGASISConfig.MAX_ROUNDS || protocol.getAliveNodesCount() == 0) {
                String leaderDisplay = roundStats.getOrDefault("leaderId",
                                       roundStats.getOrDefault("leaderInfo",
                                       roundStats.getOrDefault("totalActiveLeadersThisRound", "N/A"))).toString();
                // Using printf with %n for platform-specific newline
                System.out.printf("%s - Round: %d | Alive: %d | Leader(s): %s | Sink Tx: %s | Sink@ (%.1f, %.1f)%n", // Changed \n to %n
                        protocol.getProtocolName(), currentRound, protocol.getAliveNodesCount(),
                        leaderDisplay, roundStats.getOrDefault("bsTransmissionSuccess", "N/A"),
                        sink.getX(), sink.getY());
            }
            if(roundStats.containsKey("status") && roundStats.get("status") != null && !roundStats.get("status").toString().isEmpty()){
                 System.out.println("  Status: " + roundStats.get("status"));
            }

            if (protocol.getAliveNodesCount() == 0) {
                System.out.println(); // Spacing
                System.out.println(protocol.getProtocolName() + " ("+runSuffix+"): All nodes are dead. Ending simulation at round " + currentRound);
                if (metrics.getLndRound() == -1) metrics.setLndRound(currentRound);
                break;
            }
            if (metrics.getLndRound() != -1 && metrics.getLndRound() <= currentRound) {
                System.out.println(); // Spacing
                System.out.println(protocol.getProtocolName() + " ("+runSuffix+"): LND condition met. Ending simulation at round " + currentRound);
                break;
            }
        }

        int actualRoundsSimulated = currentRound;
        if (currentRound > PEGASISConfig.MAX_ROUNDS) {
            actualRoundsSimulated = PEGASISConfig.MAX_ROUNDS;
        } else if (metrics.getLndRound() != -1 && metrics.getLndRound() < currentRound) {
             actualRoundsSimulated = metrics.getLndRound(); // If LND happened before loop finished currentRound
        } else if (protocol.getAliveNodesCount() == 0 && metrics.getLndRound() != -1) {
            actualRoundsSimulated = metrics.getLndRound();
        } else if (protocol.getAliveNodesCount() > 0 && currentRound == PEGASISConfig.MAX_ROUNDS + 1){
            actualRoundsSimulated = PEGASISConfig.MAX_ROUNDS; // Correctly set if loop completed max rounds
        } else if (currentRound <= PEGASISConfig.MAX_ROUNDS && protocol.getAliveNodesCount() == 0){
             // Loop broke early due to all nodes dead, currentRound is the round it happened
        } else if (currentRound <= PEGASISConfig.MAX_ROUNDS && metrics.getLndRound() != -1) {
            // Loop broke early due to LND, currentRound is the round it happened
        }


        if (protocol.getAliveNodesCount() > 0 && actualRoundsSimulated == PEGASISConfig.MAX_ROUNDS && metrics.getLndRound() == -1) {
             System.out.println(); // Spacing
             System.out.println(protocol.getProtocolName() + " ("+runSuffix+"): Reached MAX_ROUNDS (" + PEGASISConfig.MAX_ROUNDS + ") with " + protocol.getAliveNodesCount() + " nodes still alive.");
        }

        System.out.println();
        System.out.println("--- Final Metrics for: " + protocol.getProtocolName() + " ("+runSuffix+", after " + actualRoundsSimulated + " rounds) ---");
        metrics.printSummary(actualRoundsSimulated);

        String protocolFileNamePart = protocol.getProtocolName().replaceAll("\\s+", "") + "_" + runSuffix;
        metrics.exportRoundByRoundDataToCSV(protocolFileNamePart + PEGASISConfig.ROUND_DATA_CSV_POSTFIX);
        metrics.appendSummaryDataToCSV(PEGASISConfig.SUMMARY_CSV_FILENAME, actualRoundsSimulated);

        System.out.println("Simulation for " + protocol.getProtocolName() + " ("+runSuffix+") finished.");
    }
}