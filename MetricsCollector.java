// File: MetricsCollector.java
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import java.util.stream.Collectors; // Not strictly needed for this version

public class MetricsCollector {

    private final int initialNodeCount;
    private String currentProtocolName = "N/A";

    // --- Network Lifetime ---
    private int fndRound = -1;
    private int hndRound = -1;
    private int lndRound = -1;

    // --- Per-Round Data Logs ---
    private final List<Integer> aliveNodesPerRoundLog = new ArrayList<>();
    private final List<Double> totalRemainingEnergyPerRoundLog = new ArrayList<>();
    private final List<Double> energyConsumedThisRoundLog = new ArrayList<>();
    private final List<String> leaderIdPerRoundLog = new ArrayList<>();
    private final List<Boolean> bsTransmissionSuccessPerRoundLog = new ArrayList<>();

    // --- Overall Performance & Energy ---
    private long totalSuccessfulTransmissionsToBS = 0;
    private long totalDataDeliveredToBS_bits = 0;
    private final Map<Integer, Double> totalEnergyConsumedByNode = new HashMap<>();
    private final Map<Integer, Double> energyConsumedAsLeaderByNode = new HashMap<>();

    // --- Fairness & Load Balancing ---
    private final Map<Integer, Integer> leaderSelectionCounts = new HashMap<>();
    private final Map<Integer, Map<String, Long>> dataOperationsPerNode = new HashMap<>();

    // --- Computational Metrics ---
    private final List<Long> roundExecutionTimesNanos = new ArrayList<>();
    private final List<Long> chainFormationTimesNanos = new ArrayList<>();

    public MetricsCollector(int initialNodeCount) {
        this.initialNodeCount = initialNodeCount;
    }

    public void setCurrentProtocolName(String protocolName) {
        this.currentProtocolName = protocolName;
    }

    // --- Logging Methods ---
    public void logRoundStart(int roundNumber) { /* ... */ }
    public void logAliveNodes(int aliveCount) { this.aliveNodesPerRoundLog.add(aliveCount); }
    public void logTotalRemainingEnergy(double totalEnergy) { this.totalRemainingEnergyPerRoundLog.add(totalEnergy); }
    public void logEnergyConsumedThisRound(double energyConsumed) { this.energyConsumedThisRoundLog.add(energyConsumed); }
    public void logLeaderSelection(int leaderId) { this.leaderIdPerRoundLog.add(String.valueOf(leaderId)); }
    public void logNoLeaderForRound() { this.leaderIdPerRoundLog.add("N/A"); }
    public void logBSTransmissionStatus(boolean success) { this.bsTransmissionSuccessPerRoundLog.add(success); }
    public void incrementSuccessfulTransmissionsToBS() { this.totalSuccessfulTransmissionsToBS++; }
    public void addDataDeliveredToBS(long bits) { this.totalDataDeliveredToBS_bits += bits; }
    public void logNodeEnergyConsumption(int nodeId, double energySpent) { this.totalEnergyConsumedByNode.merge(nodeId, energySpent, Double::sum); }
    public void logNodeEnergyConsumptionAsLeader(int nodeId, double energySpent) { this.energyConsumedAsLeaderByNode.merge(nodeId, energySpent, Double::sum); }
    public void incrementLeaderSelectionCount(int nodeId) { this.leaderSelectionCounts.merge(nodeId, 1, Integer::sum); }
    public void logDataOperation(int nodeId, String actionType) {
        this.dataOperationsPerNode.computeIfAbsent(nodeId, k -> new HashMap<>())
                                  .merge(actionType, 1L, Long::sum);
    }
    public void addRoundExecutionTime(long nanos) { this.roundExecutionTimesNanos.add(nanos); }
    public void addChainFormationTime(long nanos) { this.chainFormationTimesNanos.add(nanos); }
    public void setFndRound(int round) { if (this.fndRound == -1) this.fndRound = round; }
    public void setHndRound(int round) { 
        if (this.hndRound == -1) {
            int currentAlive = aliveNodesPerRoundLog.isEmpty() ? initialNodeCount : aliveNodesPerRoundLog.get(aliveNodesPerRoundLog.size()-1);
            if (currentAlive <= initialNodeCount / 2.0) {
                this.hndRound = round;
            }
        }
    }
    public void setLndRound(int round) { if (this.lndRound == -1) this.lndRound = round; }

    public int getFndRound() { return fndRound; }
    public int getHndRound() { return hndRound; }
    public int getLndRound() { return lndRound; }

    public void printSummary(int totalRoundsSimulated) {
        System.out.println(); // Extra line for spacing
        System.out.println("--- Metrics Summary for: " + this.currentProtocolName + " ---");
        System.out.println("Total Rounds Simulated: " + totalRoundsSimulated);

        System.out.println(); // Extra line for spacing
        System.out.println("[Network Lifetime]");
        System.out.println("  FND (First Node Died) at Round: " + (fndRound == -1 ? "N/A" : fndRound));
        System.out.println("  HND (Half Nodes Died) at Round: " + (hndRound == -1 ? "N/A" : hndRound));
        System.out.println("  LND (Last Node Died) at Round:  " + (lndRound == -1 ? "N/A" : lndRound));
        if (!aliveNodesPerRoundLog.isEmpty()) {
             System.out.println("  Nodes alive at end: " + aliveNodesPerRoundLog.get(aliveNodesPerRoundLog.size()-1) + "/" + initialNodeCount);
        }

        System.out.println();
        System.out.println("[Throughput & Data Delivery]");
        System.out.println("  Total Successful Transmissions to BS: " + totalSuccessfulTransmissionsToBS);
        System.out.println("  Total Data Delivered to BS: " + totalDataDeliveredToBS_bits + " bits (" +
                           String.format("%.2f", totalDataDeliveredToBS_bits / 8.0 / 1024.0) + " KB)");

        System.out.println();
        System.out.println("[Energy Efficiency]");
        double totalSystemEnergyConsumed = totalEnergyConsumedByNode.values().stream().mapToDouble(d -> d).sum();
        System.out.println("  Total Energy Consumed by All Nodes: " + String.format("%.4f", totalSystemEnergyConsumed) + " J");
        if (totalRoundsSimulated > 0 && !energyConsumedThisRoundLog.isEmpty()) {
            double avgEnergyPerRoundOverall = energyConsumedThisRoundLog.stream().mapToDouble(d->d).average().orElse(0.0);
             System.out.println("  Average Energy Consumption per Round (Network-wide): " + String.format("%.6f", avgEnergyPerRoundOverall) + " J");
        } else if (totalRoundsSimulated > 0) {
            System.out.println("  Average Energy Consumption per Round (Network-wide): " + String.format("%.6f", totalSystemEnergyConsumed / totalRoundsSimulated) + " J");
        }
        
        System.out.println();
        System.out.println("[Fairness & Load Balancing]");
        System.out.println("  Leader Selection Counts (NodeID: Times):");
        leaderSelectionCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> System.out.println("    Node " + entry.getKey() + ": " + entry.getValue()));

        System.out.println();
        System.out.println("[Computational Complexity]");
        if (!chainFormationTimesNanos.isEmpty()) {
             System.out.println("  Average Chain Formation Time: " +
                                String.format("%.2f", chainFormationTimesNanos.stream().mapToLong(l->l).average().orElse(0) / 1_000_000.0) + " ms");
        }
        if (!roundExecutionTimesNanos.isEmpty()) {
            double avgRoundTimeMs = roundExecutionTimesNanos.stream().mapToLong(l->l).average().orElse(0) / 1_000_000.0;
            System.out.println("  Average Execution Time per Round: " + String.format("%.2f", avgRoundTimeMs) + " ms");
        }
        System.out.println("--- End of Summary for " + this.currentProtocolName + " ---");
    }

    public void exportRoundByRoundDataToCSV(String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Protocol,Round,AliveNodes,TotalRemainingEnergy,EnergyConsumedThisRound,LeaderID,BSTransmissionSuccess");

            int numRoundsLogged = aliveNodesPerRoundLog.size();
            for (int i = 0; i < numRoundsLogged; i++) {
                // Using printf with %n for platform-specific newline
                writer.printf("%s,%d,%d,%.4f,%.6f,%s,%b%n", // Changed \n to %n
                        this.currentProtocolName,
                        (i + 1), 
                        getLoggedValue(aliveNodesPerRoundLog, i, 0),
                        getLoggedValue(totalRemainingEnergyPerRoundLog, i, 0.0),
                        getLoggedValue(energyConsumedThisRoundLog, i, 0.0),
                        getLoggedValue(leaderIdPerRoundLog, i, "N/A"),
                        getLoggedValue(bsTransmissionSuccessPerRoundLog, i, false)
                );
            }
            System.out.println("SUCCESS: Round-by-round data for " + this.currentProtocolName + " exported to " + filename);
        } catch (IOException e) {
            System.err.println("ERROR: Writing round-by-round CSV for " + this.currentProtocolName + " to " + filename + ": " + e.getMessage());
        }
    }

    public void appendSummaryDataToCSV(String filename, int totalRoundsSimulated) {
        File summaryFile = new File(filename);
        boolean writeHeader = !summaryFile.exists() || summaryFile.length() == 0;

        try (PrintWriter writer = new PrintWriter(new FileWriter(summaryFile, true))) {
            if (writeHeader) {
                writer.println("ProtocolName,TotalRoundsSimulated,FND_Round,HND_Round,LND_Round," +
                               "TotalSuccessfulTransmissionsToBS,TotalDataDeliveredToBS_bits," +
                               "TotalEnergyConsumed_AllNodes_J,AvgEnergyPerRound_J,AvgRoundExecTime_ms," +
                               "AvgChainFormationTime_ms");
            }

            double totalSystemEnergyConsumed = totalEnergyConsumedByNode.values().stream().mapToDouble(d -> d).sum();
            double avgEnergyPerRoundOverall;
            if (totalRoundsSimulated > 0 && !energyConsumedThisRoundLog.isEmpty() && energyConsumedThisRoundLog.size() >= totalRoundsSimulated) { // Check size also
                 avgEnergyPerRoundOverall = energyConsumedThisRoundLog.stream().mapToDouble(d->d).average().orElse(0.0);
            } else if (totalRoundsSimulated > 0) {
                avgEnergyPerRoundOverall = totalSystemEnergyConsumed / totalRoundsSimulated;
            } else { avgEnergyPerRoundOverall = 0.0; }
            
            double avgRoundExecTimeMs = roundExecutionTimesNanos.stream().mapToLong(l->l).average().orElse(0) / 1_000_000.0;
            double avgChainFormationTimeMs = chainFormationTimesNanos.stream().mapToLong(l->l).average().orElse(0) / 1_000_000.0;

            // Using printf with %n for platform-specific newline
            writer.printf("%s,%d,%s,%s,%s,%d,%d,%.4f,%.6f,%.2f,%.2f%n", // Changed \n to %n
                    this.currentProtocolName,
                    totalRoundsSimulated,
                    fndRound == -1 ? "N/A" : String.valueOf(fndRound),
                    hndRound == -1 ? "N/A" : String.valueOf(hndRound),
                    lndRound == -1 ? "N/A" : String.valueOf(lndRound),
                    totalSuccessfulTransmissionsToBS,
                    totalDataDeliveredToBS_bits,
                    totalSystemEnergyConsumed,
                    avgEnergyPerRoundOverall,
                    avgRoundExecTimeMs,
                    avgChainFormationTimeMs
            );
            System.out.println("SUCCESS: Summary data for " + this.currentProtocolName + " appended to " + filename);
        } catch (IOException e) {
            System.err.println("ERROR: Appending summary CSV for " + this.currentProtocolName + " to " + filename + ": " + e.getMessage());
        }
    }

    private <T> T getLoggedValue(List<T> logList, int index, T defaultValue) { return (index < logList.size()) ? logList.get(index) : defaultValue; }
    
    public void reset() { 
        fndRound = -1; hndRound = -1; lndRound = -1;
        aliveNodesPerRoundLog.clear(); totalRemainingEnergyPerRoundLog.clear(); energyConsumedThisRoundLog.clear();
        leaderIdPerRoundLog.clear(); bsTransmissionSuccessPerRoundLog.clear();
        totalSuccessfulTransmissionsToBS = 0; totalDataDeliveredToBS_bits = 0;
        totalEnergyConsumedByNode.clear(); energyConsumedAsLeaderByNode.clear(); leaderSelectionCounts.clear();
        dataOperationsPerNode.clear(); roundExecutionTimesNanos.clear(); chainFormationTimesNanos.clear();
        currentProtocolName = "N/A";
        // System.out.println("MetricsCollector has been reset."); // Keep or remove debug as preferred
    }
}