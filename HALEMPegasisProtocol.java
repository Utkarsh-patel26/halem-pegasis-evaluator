// File: HALEMPegasisProtocol.java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class HALEMPegasisProtocol implements Protocol {

    private List<SensorNode> allNodes;
    private BaseStation sinkNode;
    private MetricsCollector metrics;
    private Random protocolRandom;

    private List<Zone> zones;
    private Map<Integer, Integer> leaderCooldownMap;

    private List<SensorNode> upperChainOfZoneLeaders;
    private SensorNode superLeader;

    private static final String OP_TX_ZONE_CHAIN = "tx_zone_chain";
    private static final String OP_RX_ZONE_CHAIN = "rx_zone_chain";
    private static final String OP_AGGREGATE_ZONE = "agg_zone";
    private static final String OP_TX_ZONE_LEADER_TO_SINK = "tx_zl_to_sink";
    private static final String OP_TX_ZONE_LEADER_TO_UPPER_CHAIN = "tx_zl_to_upper";
    private static final String OP_RX_UPPER_CHAIN = "rx_upper_chain";
    private static final String OP_AGGREGATE_UPPER_CHAIN_NODE = "agg_upper_node";
    private static final String OP_AGGREGATE_SUPER_LEADER = "agg_super_leader";
    private static final String OP_TX_SUPER_LEADER_TO_SINK = "tx_sl_to_sink";

    public HALEMPegasisProtocol() {
        this.zones = new ArrayList<>();
        this.leaderCooldownMap = new HashMap<>();
        this.upperChainOfZoneLeaders = new ArrayList<>();
    }

    @Override
    public void setup(List<SensorNode> initialNodes, BaseStation currentSink, MetricsCollector mc, Random protocolRandomSeed) {
        // ... (setup logic remains the same as previous version) ...
        this.allNodes = initialNodes; this.sinkNode = currentSink; this.metrics = mc; this.protocolRandom = protocolRandomSeed;
        this.metrics.setCurrentProtocolName(getProtocolName());
        System.out.println(getProtocolName() + " setup started...");
        formZones();
        formIntraZoneChains(this.sinkNode);
        System.out.println(getProtocolName() + " setup complete. Zones: " + this.zones.size());
        // (Optional: detailed zone printout)
    }

    @Override
    public String getProtocolName() { return "HALEM-PEGASIS"; }
    @Override
    public List<SensorNode> getNodes() { return this.allNodes; }
    @Override
    public long getAliveNodesCount() { return this.allNodes.stream().filter(SensorNode::isAlive).count(); }

    private double distance(SensorNode n1, SensorNode n2) { /* ... same ... */ 
        if (n1 == null || n2 == null) return Double.MAX_VALUE;
        return Math.sqrt(Math.pow(n1.getX() - n2.getX(), 2) + Math.pow(n1.getY() - n2.getY(), 2));
    }
    private double distance(SensorNode n, BaseStation bs) { /* ... same ... */ 
        if (n == null || bs == null) return Double.MAX_VALUE;
        return Math.sqrt(Math.pow(n.getX() - bs.getX(), 2) + Math.pow(n.getY() - bs.getY(), 2));
    }
    private double distance(SensorNode n, double x, double y) { /* ... same ... */ 
        if (n == null) return Double.MAX_VALUE;
        return Math.sqrt(Math.pow(n.getX() - x, 2) + Math.pow(n.getY() - y, 2));
    }
    private void formZones() { /* ... same ... */ 
        this.zones.clear();
        int numRows = PEGASISConfig.HALEM_ZONE_ROWS; int numCols = PEGASISConfig.HALEM_ZONE_COLS;
        if (numRows <= 0 || numCols <= 0) { numRows = 1; numCols = 1; }
        int totalZones = numRows * numCols;
        double zoneWidth = PEGASISConfig.AREA_WIDTH / numCols; double zoneHeight = PEGASISConfig.AREA_HEIGHT / numRows;
        for (int i = 0; i < totalZones; i++) {
            int r = i / numCols; int c = i % numCols;
            this.zones.add(new Zone(i + 1, (c + 0.5) * zoneWidth, (r + 0.5) * zoneHeight, this));
        }
        for (SensorNode node : this.allNodes) {
            if (!node.isAlive()) continue;
            int c = Math.max(0, Math.min((int) (node.getX() / zoneWidth), numCols - 1));
            int r = Math.max(0, Math.min((int) (node.getY() / zoneHeight), numRows - 1));
            this.zones.get(r * numCols + c).addNode(node);
        }
        this.zones.forEach(Zone::updateActualCenter);
    }
    private void formIntraZoneChains(BaseStation referencePoint) { /* ... same ... */
        long totalChainTime = this.zones.stream()
            .filter(zone -> zone.getNodesInZone().stream().anyMatch(SensorNode::isAlive))
            .mapToLong(zone -> {
                long start = System.nanoTime();
                zone.formInternalChain(referencePoint);
                return System.nanoTime() - start;
            }).sum();
        if (this.metrics != null && totalChainTime > 0) this.metrics.addChainFormationTime(totalChainTime);
     }

    @Override
    public Map<String, Object> runSingleRound(int roundNumber) {
        // ... (start of runSingleRound logic remains same: metrics.logRoundStart, energy accumulator, cooldown decrement, role reset)
        if (this.metrics != null) this.metrics.logRoundStart(roundNumber);
        double totalEnergyConsumedThisRoundGlobally = 0.0;
        Map<String, Object> roundStats = new HashMap<>();
        roundStats.put("roundNumber", roundNumber); roundStats.put("protocolName", getProtocolName());

        leaderCooldownMap.keySet().removeIf(nodeId -> (leaderCooldownMap.merge(nodeId, -1, Integer::sum) <= 0));
        allNodes.stream().filter(SensorNode::isAlive).forEach(node -> node.setRole(SensorNode.Role.FOLLOWER));

        List<SensorNode> actingZoneLeadersThisRound = new ArrayList<>();
        List<SensorNode> zoneLeadersOptingForHierarchy = new ArrayList<>();
        List<String> currentRoundLeaderInfo = new ArrayList<>();
        boolean anyDirectSinkTransmissionSuccessful = false;

        // Phase 1: Intra-zone operations and leader decisions
        for (Zone zone : this.zones) {
            zone.setEffectiveLeaderForRound(null);
            if (zone.getNodesInZone().stream().noneMatch(SensorNode::isAlive)) continue;

            zone.selectZoneLeaders(roundNumber, this.metrics, this.sinkNode, this.leaderCooldownMap);
            SensorNode primaryLeader = zone.getZoneLeader();
            SensorNode backupLeader = zone.getBackupZoneLeader();
            SensorNode currentActingLeader = null;

            if (primaryLeader != null) {
                currentActingLeader = primaryLeader;
                // Primary leader's role set after checking if it will act
            }

            double energyGathering = zone.gatherDataToLeaderAndConsumeEnergy(this.metrics, primaryLeader); // Gather towards primary
            totalEnergyConsumedThisRoundGlobally += energyGathering;

            if (currentActingLeader == null || !currentActingLeader.isAlive()) {
                if (PEGASISConfig.HALEM_ENABLE_BACKUP_LEADERS && backupLeader != null && backupLeader.isAlive()) {
                    if(primaryLeader != null && primaryLeader.isAlive()) primaryLeader.setRole(SensorNode.Role.FOLLOWER); // Unset primary if it failed before acting for sink
                    currentActingLeader = backupLeader;
                    if(metrics!=null) metrics.incrementLeaderSelectionCount(currentActingLeader.getId()); // Count backup
                    currentActingLeader.incrementTimesAsLeader();
                    currentRoundLeaderInfo.add(currentActingLeader.getId() + "(B)");
                } else {
                    currentActingLeader = null;
                }
            } else {
                 currentRoundLeaderInfo.add(currentActingLeader.getId() + "(P)");
            }
            
            zone.setEffectiveLeaderForRound(currentActingLeader);

            if (currentActingLeader != null && currentActingLeader.isAlive()) {
                currentActingLeader.setRole(SensorNode.Role.LEADER); // Set role for the one ACTING
                actingZoneLeadersThisRound.add(currentActingLeader);
                if (PEGASISConfig.HALEM_ENABLE_UPPER_HIERARCHY &&
                    distance(currentActingLeader, this.sinkNode) > PEGASISConfig.HALEM_SINK_DISTANCE_THRESHOLD_FOR_HIERARCHY) {
                    zoneLeadersOptingForHierarchy.add(currentActingLeader);
                } else {
                    Zone.LeaderTransmissionResult txResult = zone.leaderTransmitsDataToSink(currentActingLeader, this.sinkNode, this.metrics);
                    totalEnergyConsumedThisRoundGlobally += txResult.energyConsumed;
                    if (txResult.successful) anyDirectSinkTransmissionSuccessful = true;
                    if (txResult.energyConsumed >= 0 || !currentActingLeader.isAlive())
                        this.leaderCooldownMap.put(currentActingLeader.getId(), PEGASISConfig.HALEM_LEADER_COOLDOWN_ROUNDS);
                }
            }
        }

        // Phase 2: Upper Hierarchy operations
        this.superLeader = null;
        this.upperChainOfZoneLeaders.clear();
        boolean superLeaderTransmittedSuccessfully = false;

        if (PEGASISConfig.HALEM_ENABLE_UPPER_HIERARCHY && zoneLeadersOptingForHierarchy.size() >= PEGASISConfig.HALEM_MIN_LEADERS_FOR_UPPER_CHAIN) {
            this.upperChainOfZoneLeaders = formUpperChain(zoneLeadersOptingForHierarchy, this.sinkNode);
            if (!this.upperChainOfZoneLeaders.isEmpty()) {
                this.superLeader = selectSuperLeader(this.upperChainOfZoneLeaders, roundNumber);
                if (this.superLeader != null) {
                    currentRoundLeaderInfo.add(this.superLeader.getId() + "(SL)");
                    this.superLeader.setRole(SensorNode.Role.LEADER);
                    if(metrics!=null) metrics.incrementLeaderSelectionCount(this.superLeader.getId());
                    this.superLeader.incrementTimesAsLeader();

                    double energyUpperChainTx = transmitDataAlongUpperChainToSuperLeader(
                                                    this.upperChainOfZoneLeaders, this.superLeader, this.metrics);
                    totalEnergyConsumedThisRoundGlobally += energyUpperChainTx;

                    if (this.superLeader.isAlive()) {
                        Zone.LeaderTransmissionResult slTxResult = transmitFromSuperLeaderToSink(
                                                                    this.superLeader, this.sinkNode, this.metrics);
                        totalEnergyConsumedThisRoundGlobally += slTxResult.energyConsumed;
                        if (slTxResult.successful) superLeaderTransmittedSuccessfully = true;
                        if(slTxResult.energyConsumed >=0 || !this.superLeader.isAlive())
                            this.leaderCooldownMap.put(this.superLeader.getId(), PEGASISConfig.HALEM_LEADER_COOLDOWN_ROUNDS);
                    }
                }
            }
        } else if (PEGASISConfig.HALEM_ENABLE_UPPER_HIERARCHY) {
            for (SensorNode zl : zoneLeadersOptingForHierarchy) {
                if (zl.isAlive() && !this.leaderCooldownMap.containsKey(zl.getId())) {
                    Zone zone = findZoneForLeader(zl);
                    if (zone != null) {
                        Zone.LeaderTransmissionResult txResult = zone.leaderTransmitsDataToSink(zl, this.sinkNode, this.metrics);
                        totalEnergyConsumedThisRoundGlobally += txResult.energyConsumed;
                        if (txResult.successful) anyDirectSinkTransmissionSuccessful = true;
                         if (txResult.energyConsumed >= 0 || !zl.isAlive())
                            this.leaderCooldownMap.put(zl.getId(), PEGASISConfig.HALEM_LEADER_COOLDOWN_ROUNDS);
                    }
                }
            }
        }
        
        boolean overallSinkSuccess = anyDirectSinkTransmissionSuccessful || superLeaderTransmittedSuccessfully;
        roundStats.put("leaderInfo", currentRoundLeaderInfo.isEmpty() ? "N/A" : String.join(", ", currentRoundLeaderInfo));
        roundStats.put("bsTransmissionSuccess", overallSinkSuccess);

        if (this.metrics != null) {
            if (actingZoneLeadersThisRound.isEmpty() && this.superLeader == null) metrics.logNoLeaderForRound();
            else if (!currentRoundLeaderInfo.isEmpty()) {
                try { // Attempt to parse ID, robust to (P)/(B)/(SL) tags
                    String firstLeaderStr = currentRoundLeaderInfo.get(0).split("\\(")[0];
                    metrics.logLeaderSelection(Integer.parseInt(firstLeaderStr));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    metrics.logLeaderSelection(-1); // Fallback if parsing fails
                }
            } else {
                 metrics.logLeaderSelection(-1); // Fallback
            }
            metrics.logBSTransmissionStatus(overallSinkSuccess);
        }

        // ... (rest of runSingleRound: aliveCount, energy logging, FND/HND/LND checks remain the same) ...
        long aliveCount = getAliveNodesCount();
        double totalRemainingNetworkEnergy = allNodes.stream().filter(SensorNode::isAlive).mapToDouble(SensorNode::getEnergy).sum();

        if (this.metrics != null) {
            metrics.logAliveNodes((int) aliveCount);
            metrics.logTotalRemainingEnergy(totalRemainingNetworkEnergy);
            metrics.logEnergyConsumedThisRound(totalEnergyConsumedThisRoundGlobally);
            if (metrics.getFndRound() == -1 && aliveCount < PEGASISConfig.NUM_NODES) metrics.setFndRound(roundNumber);
            if (metrics.getHndRound() == -1 && aliveCount <= (PEGASISConfig.NUM_NODES / 2.0)) metrics.setHndRound(roundNumber);
            if (aliveCount == 0 && metrics.getLndRound() == -1) metrics.setLndRound(roundNumber);
        }
        return roundStats;
    }

    private Zone findZoneForLeader(SensorNode leaderNode) { /* ... same as before ... */ 
        if (leaderNode == null) return null;
        for (Zone zone : this.zones) {
            if (zone.getEffectiveLeaderForRound() != null && zone.getEffectiveLeaderForRound().getId() == leaderNode.getId()) return zone;
            if (zone.getZoneLeader() != null && zone.getZoneLeader().getId() == leaderNode.getId()) return zone;
            if (zone.getBackupZoneLeader() != null && zone.getBackupZoneLeader().getId() == leaderNode.getId()) return zone;
        }
        for (Zone zone : this.zones) {
            if (zone.getNodesInZone().stream().anyMatch(n -> n.getId() == leaderNode.getId())) return zone;
        }
        return null;
    }

    private List<SensorNode> formUpperChain(List<SensorNode> ZLs, BaseStation sinkRef) {
        long startTime = System.nanoTime();
        List<SensorNode> upperChain = new ArrayList<>();
        if (ZLs.isEmpty()) {
            if (this.metrics != null) this.metrics.addChainFormationTime(System.nanoTime() - startTime);
            return upperChain;
        }
        List<SensorNode> availableZLs = new ArrayList<>(ZLs.stream().filter(SensorNode::isAlive).collect(Collectors.toList()));
        if (availableZLs.isEmpty()) {
            if (this.metrics != null) this.metrics.addChainFormationTime(System.nanoTime() - startTime);
            return upperChain;
        }

        SensorNode startNode = Collections.max(availableZLs, Comparator.comparingDouble(n -> distance(n, sinkRef)));
        upperChain.add(startNode);
        availableZLs.remove(startNode);

        SensorNode currentChainEndLocal = startNode; // Use a local variable for this loop
        while (!availableZLs.isEmpty()) {
            SensorNode closest = null;
            double minDistance = Double.MAX_VALUE;
            for (SensorNode candidate : availableZLs) { // Traditional loop to find closest
                double d = distance(currentChainEndLocal, candidate);
                if (d < minDistance) {
                    minDistance = d;
                    closest = candidate;
                }
            }
            if (closest != null) {
                upperChain.add(closest);
                availableZLs.remove(closest);
                currentChainEndLocal = closest; // Update local loop variable
            } else {
                break;
            }
        }
        if (this.metrics != null) this.metrics.addChainFormationTime(System.nanoTime() - startTime);
        return upperChain;
    }

    private SensorNode selectSuperLeader(List<SensorNode> upperChain, int roundNum) { /* ... same as before ... */ 
        if (upperChain.isEmpty()) return null;
        List<SensorNode> aliveInUpper = upperChain.stream().filter(SensorNode::isAlive).collect(Collectors.toList());
        if(aliveInUpper.isEmpty()) return null;
        int slIdx = (roundNum - 1 + zones.size()) % aliveInUpper.size();
        return aliveInUpper.get(slIdx);
    }

    private double transmitDataAlongUpperChainToSuperLeader(List<SensorNode> chainOfZLs, SensorNode superLeader, MetricsCollector metrics) { /* ... same as before ... */ 
        if (superLeader == null || !superLeader.isAlive() || chainOfZLs.isEmpty() || chainOfZLs.size() < 1 || (chainOfZLs.size() == 1 && chainOfZLs.get(0).getId() == superLeader.getId())) {
             // If only SL is in chain, no transmission along chain needed.
            return 0.0;
        }
        double energyConsumedThisPhase = 0.0;
        int superLeaderChainIndex = -1;
        for (int i = 0; i < chainOfZLs.size(); i++) {
            if (chainOfZLs.get(i).getId() == superLeader.getId()) { superLeaderChainIndex = i; break; }
        }
        if (superLeaderChainIndex == -1) { System.err.println("HALEM Error: Super Leader " + superLeader.getId() + " not in its upper chain."); return 0.0; }
        for (int i = superLeaderChainIndex - 1; i >= 0; i--) {
            SensorNode senderZL = chainOfZLs.get(i); SensorNode receiverZL = chainOfZLs.get(i + 1);
            energyConsumedThisPhase += transmitReceiveAggregateOneHopUpper(senderZL, receiverZL, (receiverZL.getId() == superLeader.getId()), metrics);
            if (!senderZL.isAlive() || !receiverZL.isAlive()) break;
        }
        for (int i = superLeaderChainIndex + 1; i < chainOfZLs.size(); i++) {
            SensorNode senderZL = chainOfZLs.get(i); SensorNode receiverZL = chainOfZLs.get(i - 1);
            energyConsumedThisPhase += transmitReceiveAggregateOneHopUpper(senderZL, receiverZL, (receiverZL.getId() == superLeader.getId()), metrics);
            if (!senderZL.isAlive() || !receiverZL.isAlive()) break;
        }
        return energyConsumedThisPhase;
    }

    private double transmitReceiveAggregateOneHopUpper(SensorNode senderZL, SensorNode receiverZL, boolean isReceiverSuperLeader, MetricsCollector metrics) { /* ... same as before ... */ 
        if (!senderZL.isAlive() || !receiverZL.isAlive()) return 0.0;
        double hopEnergy = 0.0; double dist = distance(senderZL, receiverZL); int packetSize = PEGASISConfig.DATA_PACKET_SIZE_BITS;
        double energyTx = (PEGASISConfig.E_ELEC * packetSize) + (PEGASISConfig.E_AMP * packetSize * dist * dist);
        if (senderZL.getEnergy() >= energyTx) {
            senderZL.consumeEnergy(energyTx); senderZL.incrementPacketsSent(); hopEnergy += energyTx;
            if (metrics != null) { metrics.logNodeEnergyConsumption(senderZL.getId(), energyTx); metrics.logDataOperation(senderZL.getId(), OP_TX_ZONE_LEADER_TO_UPPER_CHAIN); }
        } else { double rem = senderZL.getEnergy(); senderZL.consumeEnergy(rem); hopEnergy += rem; if (metrics != null) metrics.logNodeEnergyConsumption(senderZL.getId(), rem); return hopEnergy; }
        double energyRx = PEGASISConfig.E_ELEC * packetSize;
        if (receiverZL.getEnergy() >= energyRx) {
            receiverZL.consumeEnergy(energyRx); receiverZL.incrementPacketsReceived(); hopEnergy += energyRx;
            if (metrics != null) { metrics.logNodeEnergyConsumption(receiverZL.getId(), energyRx); metrics.logDataOperation(receiverZL.getId(), OP_RX_UPPER_CHAIN); }
        } else { double rem = receiverZL.getEnergy(); receiverZL.consumeEnergy(rem); hopEnergy += rem; if (metrics != null) metrics.logNodeEnergyConsumption(receiverZL.getId(), rem); return hopEnergy; }
        double energyDa = PEGASISConfig.E_DA * packetSize;
        if (receiverZL.getEnergy() >= energyDa) {
            receiverZL.consumeEnergy(energyDa); hopEnergy += energyDa;
            String aggOp = isReceiverSuperLeader ? OP_AGGREGATE_SUPER_LEADER : OP_AGGREGATE_UPPER_CHAIN_NODE;
            if (metrics != null) { metrics.logNodeEnergyConsumption(receiverZL.getId(), energyDa); metrics.logDataOperation(receiverZL.getId(), aggOp); }
        } else { double rem = receiverZL.getEnergy(); receiverZL.consumeEnergy(rem); hopEnergy += rem; if (metrics != null) metrics.logNodeEnergyConsumption(receiverZL.getId(), rem); }
        return hopEnergy;
    }

    private Zone.LeaderTransmissionResult transmitFromSuperLeaderToSink(SensorNode sl, BaseStation sink, MetricsCollector metrics) { /* ... same as before ... */ 
        if (sl == null || !sl.isAlive() || sink == null) return new Zone.LeaderTransmissionResult(false, 0.0);
        double energyConsumedBySLTx = 0.0; boolean transmissionSuccessful = false;
        double distToSink = distance(sl, sink); int packetSize = PEGASISConfig.DATA_PACKET_SIZE_BITS;
        double energyTxToSink = (PEGASISConfig.E_ELEC * packetSize) + (PEGASISConfig.E_AMP * packetSize * distToSink * distToSink);
        if (sl.getEnergy() >= energyTxToSink) {
            sl.consumeEnergy(energyTxToSink); sl.incrementPacketsSent();
            energyConsumedBySLTx = energyTxToSink; transmissionSuccessful = true;
            if (metrics != null) {
                metrics.logNodeEnergyConsumption(sl.getId(), energyTxToSink);
                metrics.logNodeEnergyConsumptionAsLeader(sl.getId(), energyTxToSink);
                metrics.logDataOperation(sl.getId(), OP_TX_SUPER_LEADER_TO_SINK);
            }
            sink.receiveData(sl, packetSize, metrics);
        } else {
            energyConsumedBySLTx = sl.getEnergy(); sl.consumeEnergy(energyConsumedBySLTx); transmissionSuccessful = false;
            if (metrics != null && energyConsumedBySLTx > 0) {
                metrics.logNodeEnergyConsumption(sl.getId(), energyConsumedBySLTx);
                metrics.logNodeEnergyConsumptionAsLeader(sl.getId(), energyConsumedBySLTx);
                metrics.logDataOperation(sl.getId(), OP_TX_SUPER_LEADER_TO_SINK);
            }
        }
        return new Zone.LeaderTransmissionResult(transmissionSuccessful, energyConsumedBySLTx);
    }

    // --- Inner Class for Zone ---
    private static class Zone {
        final int id; List<SensorNode> nodesInZone; List<SensorNode> chain;
        SensorNode zoneLeader; SensorNode backupZoneLeader; SensorNode effectiveLeaderForRound;
        final double definedCenterX, definedCenterY; double actualCenterX, actualCenterY;
        private HALEMPegasisProtocol parentProtocol;
        static class LeaderTransmissionResult { /* ... same ... */ boolean successful; double energyConsumed; LeaderTransmissionResult(boolean s, double e){successful=s; energyConsumed=e;} }

        Zone(int id, double dX, double dY, HALEMPegasisProtocol p) { /* ... same ... */ 
            this.id=id; this.definedCenterX=dX; this.definedCenterY=dY; this.parentProtocol=p;
            this.nodesInZone=new ArrayList<>(); this.chain=new ArrayList<>();
            this.actualCenterX=dX; this.actualCenterY=dY;
        }
        void addNode(SensorNode n) { this.nodesInZone.add(n); }
        void updateActualCenter() { /* ... same ... */ 
            List<SensorNode> aliveInZone = nodesInZone.stream().filter(SensorNode::isAlive).collect(Collectors.toList());
            if (aliveInZone.isEmpty()) { this.actualCenterX=this.definedCenterX; this.actualCenterY=this.definedCenterY; return; }
            this.actualCenterX = aliveInZone.stream().mapToDouble(SensorNode::getX).average().orElse(this.definedCenterX);
            this.actualCenterY = aliveInZone.stream().mapToDouble(SensorNode::getY).average().orElse(this.definedCenterY);
        }
        
        void formInternalChain(BaseStation refPt) {
            this.chain.clear();
            List<SensorNode> alive = this.nodesInZone.stream()
                                       .filter(SensorNode::isAlive)
                                       .collect(Collectors.toCollection(ArrayList::new));
            if (alive.isEmpty()) return;

            SensorNode startNode = Collections.max(alive, Comparator.comparingDouble(n -> parentProtocol.distance(n, refPt)));
            this.chain.add(startNode);
            alive.remove(startNode);

            SensorNode currentChainEndLocal = startNode; // Use local var for loop
            while (!alive.isEmpty()) {
                SensorNode closest = null;
                double minDistance = Double.MAX_VALUE;
                for (SensorNode candidate : alive) { // Traditional loop
                    double d = parentProtocol.distance(currentChainEndLocal, candidate);
                    if (d < minDistance) {
                        minDistance = d;
                        closest = candidate;
                    }
                }
                if (closest != null) {
                    this.chain.add(closest);
                    alive.remove(closest);
                    currentChainEndLocal = closest;
                } else {
                    break;
                }
            }
        }

        void selectZoneLeaders(int roundNum, MetricsCollector mets, BaseStation currentSink, Map<Integer,Integer> cooldownMap) { /* ... same, but use mets.incrementLeaderSelectionCount ... */
            this.zoneLeader = null; this.backupZoneLeader = null;
            List<SensorNode> candidates = this.chain.stream().filter(SensorNode::isAlive)
                                           .filter(node -> !cooldownMap.containsKey(node.getId()))
                                           .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                candidates = this.chain.stream().filter(SensorNode::isAlive).collect(Collectors.toList());
                if (candidates.isEmpty()) return;
            }
            List<NodeScore> scoredCandidates = new ArrayList<>();
            final double centralityRefX = this.actualCenterX; final double centralityRefY = this.actualCenterY;
            double maxDistToRef = candidates.stream().mapToDouble(n -> parentProtocol.distance(n, centralityRefX, centralityRefY)).max().orElse(0.0);
            if (maxDistToRef == 0.0 && candidates.size() > 1) maxDistToRef = 0.001;

            for (SensorNode cand : candidates) {
                double nEgy = (cand.getInitialEnergy()>0) ? (cand.getEnergy()/cand.getInitialEnergy()) : 0.0;
                double distToRef = parentProtocol.distance(cand, centralityRefX, centralityRefY);
                double nCent = (maxDistToRef>0) ? Math.max(0,1.0-(distToRef/maxDistToRef)) : 1.0;
                double score = (PEGASISConfig.HALEM_LEADER_SCORE_W1_ENERGY*nEgy) + (PEGASISConfig.HALEM_LEADER_SCORE_W2_CENTRALITY*nCent);
                scoredCandidates.add(new NodeScore(cand, score));
            }
            if (scoredCandidates.isEmpty()) return;
            scoredCandidates.sort(Comparator.comparingDouble(NodeScore::getScore).reversed());
            this.zoneLeader = scoredCandidates.get(0).getNode();
            if (this.zoneLeader != null && mets != null) {
                mets.incrementLeaderSelectionCount(this.zoneLeader.getId()); // Corrected call
                // this.zoneLeader.incrementTimesAsLeader(); // Handled in main runSingleRound
            }
            if (PEGASISConfig.HALEM_ENABLE_BACKUP_LEADERS && scoredCandidates.size() > 1) {
                this.backupZoneLeader = scoredCandidates.get(1).getNode();
            }
        }
        private static class NodeScore { /* ... same ... */ SensorNode node; double score; NodeScore(SensorNode n, double s){node=n;score=s;} SensorNode getNode(){return node;} double getScore(){return score;}}
        
        public double gatherDataToLeaderAndConsumeEnergy(MetricsCollector metrics, SensorNode designatedLeader) { /* ... same ... */ 
            if (designatedLeader==null || !designatedLeader.isAlive() || this.chain.isEmpty()) return 0.0;
            double energyConsumed = 0.0; int leaderIdx = -1;
            for(int i=0; i<this.chain.size(); i++) if(this.chain.get(i).getId() == designatedLeader.getId()) leaderIdx = i;
            if (leaderIdx == -1) { System.err.println("Zone " + this.id + " GATHER Error: Leader " + designatedLeader.getId() + " not in chain."); return 0.0;}
            for (int i=leaderIdx-1; i>=0; i--) {
                energyConsumed += transmitReceiveAggregateOneHop(this.chain.get(i), this.chain.get(i+1), metrics);
                if (!this.chain.get(i).isAlive() || !this.chain.get(i+1).isAlive()) break;
            }
            for (int i=leaderIdx+1; i<this.chain.size(); i++) {
                energyConsumed += transmitReceiveAggregateOneHop(this.chain.get(i), this.chain.get(i-1), metrics);
                if (!this.chain.get(i).isAlive() || !this.chain.get(i-1).isAlive()) break;
            }
            return energyConsumed;
        }
        private double transmitReceiveAggregateOneHop(SensorNode s, SensorNode r, MetricsCollector mets) { /* ... same ... */ 
            if (!s.isAlive() || !r.isAlive()) return 0.0;
            double hopE = 0.0, dist = parentProtocol.distance(s,r), pSize = PEGASISConfig.DATA_PACKET_SIZE_BITS;
            double eTx = (PEGASISConfig.E_ELEC*pSize) + (PEGASISConfig.E_AMP*pSize*dist*dist);
            if(s.getEnergy()>=eTx){s.consumeEnergy(eTx);s.incrementPacketsSent();hopE+=eTx;if(mets!=null){mets.logNodeEnergyConsumption(s.getId(),eTx);mets.logDataOperation(s.getId(),OP_TX_ZONE_CHAIN);}}
            else{double rem=s.getEnergy();s.consumeEnergy(rem);hopE+=rem;if(mets!=null)mets.logNodeEnergyConsumption(s.getId(),rem);return hopE;}
            double eRx = PEGASISConfig.E_ELEC*pSize;
            if(r.getEnergy()>=eRx){r.consumeEnergy(eRx);r.incrementPacketsReceived();hopE+=eRx;if(mets!=null){mets.logNodeEnergyConsumption(r.getId(),eRx);mets.logDataOperation(r.getId(),OP_RX_ZONE_CHAIN);}}
            else{double rem=r.getEnergy();r.consumeEnergy(rem);hopE+=rem;if(mets!=null)mets.logNodeEnergyConsumption(r.getId(),rem);return hopE;}
            double eDa = PEGASISConfig.E_DA*pSize;
            if(r.getEnergy()>=eDa){r.consumeEnergy(eDa);hopE+=eDa;if(mets!=null){mets.logNodeEnergyConsumption(r.getId(),eDa);mets.logDataOperation(r.getId(),OP_AGGREGATE_ZONE);}}
            else{double rem=r.getEnergy();r.consumeEnergy(rem);hopE+=rem;if(mets!=null)mets.logNodeEnergyConsumption(r.getId(),rem);}
            return hopE;
        }
        public LeaderTransmissionResult leaderTransmitsDataToSink(SensorNode actingLeader, BaseStation sink, MetricsCollector metrics) { /* ... same ... */ 
             if (actingLeader == null || !actingLeader.isAlive() || sink == null) return new LeaderTransmissionResult(false, 0.0);
            double energyConsumed = 0.0; boolean success = false;
            double distToSink = parentProtocol.distance(actingLeader, sink);
            int packetSize = PEGASISConfig.DATA_PACKET_SIZE_BITS;
            double eTxToSink = (PEGASISConfig.E_ELEC*packetSize) + (PEGASISConfig.E_AMP*packetSize*distToSink*distToSink);
            if (actingLeader.getEnergy() >= eTxToSink) {
                actingLeader.consumeEnergy(eTxToSink); actingLeader.incrementPacketsSent();
                energyConsumed = eTxToSink; success = true;
                if (metrics != null) {
                    metrics.logNodeEnergyConsumption(actingLeader.getId(), eTxToSink);
                    metrics.logNodeEnergyConsumptionAsLeader(actingLeader.getId(), eTxToSink);
                    metrics.logDataOperation(actingLeader.getId(), OP_TX_ZONE_LEADER_TO_SINK);
                }
                sink.receiveData(actingLeader, packetSize, metrics);
            } else {
                energyConsumed = actingLeader.getEnergy(); actingLeader.consumeEnergy(energyConsumed); success = false;
                if (metrics != null && energyConsumed > 0) {
                    metrics.logNodeEnergyConsumption(actingLeader.getId(), energyConsumed);
                    metrics.logNodeEnergyConsumptionAsLeader(actingLeader.getId(), energyConsumed);
                    metrics.logDataOperation(actingLeader.getId(), OP_TX_ZONE_LEADER_TO_SINK);
                }
            }
            return new LeaderTransmissionResult(success, energyConsumed);
        }

        public int getId() { return id; } public List<SensorNode> getNodesInZone() { return nodesInZone; }
        public List<SensorNode> getChain() { return chain; } public SensorNode getZoneLeader() { return zoneLeader; }
        public SensorNode getBackupZoneLeader() { return backupZoneLeader; }
        public SensorNode getEffectiveLeaderForRound() { return effectiveLeaderForRound; }
        public void setEffectiveLeaderForRound(SensorNode leader) { this.effectiveLeaderForRound = leader; }
        public double getActualCenterX() { return actualCenterX; } public double getActualCenterY() { return actualCenterY; }
    }
}