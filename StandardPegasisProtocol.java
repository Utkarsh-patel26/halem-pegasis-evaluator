// File: StandardPegasisProtocol.java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class StandardPegasisProtocol implements Protocol {

    private List<SensorNode> nodes;
    private List<SensorNode> chain; // Current PEGASIS chain
    private BaseStation baseStation;
    private MetricsCollector metrics;
    private Random protocolRandom; // For any internal randomization, if needed

    private SensorNode currentLeader;
    private int lastChainReformationRound;
    private double currentRoundTotalEnergyConsumed; // Accumulates energy consumed in the current round

    private static final String OP_TX_CHAIN = "tx_chain";
    private static final String OP_RX_CHAIN = "rx_chain";
    private static final String OP_AGGREGATE = "aggregate";
    private static final String OP_TX_BS = "tx_bs";


    public StandardPegasisProtocol() {
        this.chain = new ArrayList<>();
        this.lastChainReformationRound = 0;
    }

    @Override
    public void setup(List<SensorNode> initialNodes, BaseStation bs, MetricsCollector mc, Random protocolRandomSeed) {
        this.nodes = initialNodes; // Expects a fresh list of nodes
        this.baseStation = bs;
        this.metrics = mc;
        this.protocolRandom = protocolRandomSeed;
        this.metrics.setCurrentProtocolName(getProtocolName());

        // Initial chain formation
        if (getAliveNodesCount() > 0) {
            formChain();
            this.lastChainReformationRound = 0; // Or 1 if setup is considered before round 1
        }
        System.out.println(getProtocolName() + " setup complete. Initial chain size: " + this.chain.size());
    }

    @Override
    public String getProtocolName() {
        return "Standard PEGASIS";
    }

    @Override
    public List<SensorNode> getNodes() {
        return this.nodes;
    }

    @Override
    public long getAliveNodesCount() {
        return this.nodes.stream().filter(SensorNode::isAlive).count();
    }

    // --- Distance Utilities ---
    private double distance(SensorNode n1, SensorNode n2) {
        return Math.sqrt(Math.pow(n1.getX() - n2.getX(), 2) + Math.pow(n1.getY() - n2.getY(), 2));
    }

    private double distance(SensorNode n, BaseStation bs) {
        return Math.sqrt(Math.pow(n.getX() - bs.getX(), 2) + Math.pow(n.getY() - bs.getY(), 2));
    }

    // --- Core PEGASIS Logic ---

    /**
     * Forms the PEGASIS chain using a greedy algorithm.
     * Starts with a node far from the BS (or a corner if BS is at 0,0).
     * Iteratively adds the closest alive, unchained node.
     */
    private void formChain() {
        long startTime = System.nanoTime();
        this.chain.clear();
        List<SensorNode> availableNodes = this.nodes.stream()
                                                 .filter(SensorNode::isAlive)
                                                 .collect(Collectors.toCollection(ArrayList::new));

        if (availableNodes.isEmpty()) {
            if (this.metrics != null) this.metrics.addChainFormationTime(System.nanoTime() - startTime);
            return;
        }

        // Find starting node: farthest from Base Station
        SensorNode startNode = Collections.max(availableNodes,
                Comparator.comparingDouble(n -> distance(n, this.baseStation)));

        this.chain.add(startNode);
        availableNodes.remove(startNode);

        SensorNode currentChainEnd = startNode;
        while (!availableNodes.isEmpty()) {
            SensorNode closest = null;
            double minDistance = Double.MAX_VALUE;
            for (SensorNode candidate : availableNodes) {
                double d = distance(currentChainEnd, candidate);
                if (d < minDistance) {
                    minDistance = d;
                    closest = candidate;
                }
            }
            if (closest != null) {
                this.chain.add(closest);
                availableNodes.remove(closest);
                currentChainEnd = closest;
            } else {
                break; // No more nodes can be added
            }
        }
        if (this.metrics != null) this.metrics.addChainFormationTime(System.nanoTime() - startTime);
    }

    /**
     * Selects a leader for the current round using round-robin on alive chain members.
     */
    private SensorNode selectLeader(int roundNumber, List<SensorNode> aliveChainMembers) {
        if (aliveChainMembers.isEmpty()) {
            return null;
        }
        int leaderIndex = (roundNumber -1) % aliveChainMembers.size(); // -1 for 0-based indexing with round 1
        SensorNode leader = aliveChainMembers.get(leaderIndex);
        leader.setRole(SensorNode.Role.LEADER);
        leader.incrementTimesAsLeader();
        if (this.metrics != null) this.metrics.logLeaderSelection(leader.getId());
        return leader;
    }

    @Override
    public Map<String, Object> runSingleRound(int roundNumber) {
        this.currentRoundTotalEnergyConsumed = 0.0;
        Map<String, Object> roundStats = new HashMap<>();
        roundStats.put("roundNumber", roundNumber);

        // 0. Reset roles from previous round (except for newly selected leader)
        for (SensorNode node : this.nodes) {
            if (node.isAlive()) {
                node.setRole(SensorNode.Role.FOLLOWER);
            }
        }

        // 1. Check for Chain Reformation
        boolean chainReformedThisRound = false;
        if (roundNumber == 1 && this.chain.isEmpty() && getAliveNodesCount() > 0) { // Initial formation if not done in setup
            formChain();
            this.lastChainReformationRound = roundNumber;
            chainReformedThisRound = true;
        } else {
            boolean needsReformation = false;
            if (PEGASISConfig.REFORM_CHAIN_ON_DEATH_PEGASIS) {
                for (SensorNode chainNode : this.chain) {
                    if (!chainNode.isAlive()) {
                        needsReformation = true;
                        break;
                    }
                }
            }
            if (!needsReformation && (roundNumber - this.lastChainReformationRound >= PEGASISConfig.REFORM_CHAIN_INTERVAL_PEGASIS)) {
                needsReformation = true;
            }

            if (needsReformation && getAliveNodesCount() > 0) {
                // System.out.println("Reforming chain in round " + roundNumber); // Debug
                formChain();
                this.lastChainReformationRound = roundNumber;
                chainReformedThisRound = true;
            }
        }
        roundStats.put("chainReformed", chainReformedThisRound);
        roundStats.put("chainSize", this.chain.size());


        // 2. Leader Selection
        List<SensorNode> aliveChainMembers = this.chain.stream().filter(SensorNode::isAlive).collect(Collectors.toList());
        if (aliveChainMembers.isEmpty()) {
            roundStats.put("status", "No alive nodes in chain to select leader.");
            metrics.logNoLeaderForRound();
            metrics.logBSTransmissionStatus(false);
            finalizeRoundMetrics(roundNumber);
            return roundStats;
        }
        this.currentLeader = selectLeader(roundNumber, aliveChainMembers);
        roundStats.put("leaderId", this.currentLeader.getId());


        // 3. Data Transmission along the chain to the leader
        int leaderChainIndex = -1;
        for (int i = 0; i < this.chain.size(); i++) {
            if (this.chain.get(i).getId() == this.currentLeader.getId()) {
                leaderChainIndex = i;
                break;
            }
        }

        if (leaderChainIndex != -1) {
            // Transmit from left side towards leader
            for (int i = leaderChainIndex - 1; i >= 0; i--) {
                SensorNode sender = this.chain.get(i);
                SensorNode receiver = this.chain.get(i + 1);
                if (sender.isAlive() && receiver.isAlive()) {
                    transmitAndAggregate(sender, receiver, false);
                } else if (sender.isAlive() && !receiver.isAlive()) {
                    break; // Path broken
                }
            }
            // Transmit from right side towards leader
            for (int i = leaderChainIndex + 1; i < this.chain.size(); i++) {
                SensorNode sender = this.chain.get(i);
                SensorNode receiver = this.chain.get(i - 1);
                if (sender.isAlive() && receiver.isAlive()) {
                    transmitAndAggregate(sender, receiver, false);
                } else if (sender.isAlive() && !receiver.isAlive()) {
                    break; // Path broken
                }
            }
        }


        // 4. Leader transmits to Base Station
        boolean bsTransmissionSuccess = false;
        if (this.currentLeader != null && this.currentLeader.isAlive()) {
            double distToBS = distance(this.currentLeader, this.baseStation);
            double energyTxBS = (PEGASISConfig.E_ELEC * PEGASISConfig.DATA_PACKET_SIZE_BITS) +
                                (PEGASISConfig.E_AMP * PEGASISConfig.DATA_PACKET_SIZE_BITS * distToBS * distToBS);

            this.currentRoundTotalEnergyConsumed += energyTxBS;
            metrics.logNodeEnergyConsumption(this.currentLeader.getId(), energyTxBS);
            metrics.logNodeEnergyConsumptionAsLeader(this.currentLeader.getId(), energyTxBS); // Specifically as leader
            metrics.logDataOperation(this.currentLeader.getId(), OP_TX_BS);


            if (this.currentLeader.consumeEnergy(energyTxBS)) {
                this.currentLeader.incrementPacketsSent();
                this.baseStation.receiveData(this.currentLeader, PEGASISConfig.DATA_PACKET_SIZE_BITS, this.metrics);
                bsTransmissionSuccess = true;
            } else {
                // Leader died trying to transmit to BS
                bsTransmissionSuccess = false;
            }
        }
        roundStats.put("bsTransmissionSuccess", bsTransmissionSuccess);
        metrics.logBSTransmissionStatus(bsTransmissionSuccess);


        // 5. Finalize round metrics (update node states, log FND/HND/LND, etc.)
        finalizeRoundMetrics(roundNumber);

        return roundStats;
    }

    /**
     * Helper for intra-chain transmission and aggregation energy consumption.
     * @param sender The sending node.
     * @param receiver The receiving node.
     * @param isReceiverTheLeader True if the receiver is the current round leader.
     */
    private void transmitAndAggregate(SensorNode sender, SensorNode receiver, boolean isReceiverTheLeader) {
        double dist = distance(sender, receiver);
        int packetSize = PEGASISConfig.DATA_PACKET_SIZE_BITS;

        // Sender transmits
        double energyTx = (PEGASISConfig.E_ELEC * packetSize) + (PEGASISConfig.E_AMP * packetSize * dist * dist);
        if (sender.isAlive()) {
             this.currentRoundTotalEnergyConsumed += energyTx;
             metrics.logNodeEnergyConsumption(sender.getId(), energyTx);
             metrics.logDataOperation(sender.getId(), OP_TX_CHAIN);
             if (sender.consumeEnergy(energyTx)) {
                 sender.incrementPacketsSent();
             } else { return; } // Sender died
        } else { return; } // Sender already dead

        // Receiver receives
        double energyRx = PEGASISConfig.E_ELEC * packetSize;
        if (receiver.isAlive()) {
            this.currentRoundTotalEnergyConsumed += energyRx;
            metrics.logNodeEnergyConsumption(receiver.getId(), energyRx);
            metrics.logDataOperation(receiver.getId(), OP_RX_CHAIN);
            if (receiver.consumeEnergy(energyRx)) {
                receiver.incrementPacketsReceived();
            } else { return; } // Receiver died
        } else { return; } // Receiver already dead


        // Receiver aggregates (if it's not the leader receiving final data, or if it's leader before BS TX)
        // In PEGASIS, the leader also aggregates data before sending to BS.
        // Non-leader nodes on the path also perform aggregation.
        // For simplicity, assume any node that receives data (and isn't just a pass-through dead-end) performs aggregation.
        // If the receiver is the leader, this is part of its data fusion before BS transmission.
        // If not the leader, it's fusing and preparing to forward.
        double energyDa = PEGASISConfig.E_DA * packetSize;
         if (receiver.isAlive()) { // Check again as it might have died from Rx
            this.currentRoundTotalEnergyConsumed += energyDa;
            metrics.logNodeEnergyConsumption(receiver.getId(), energyDa);
            metrics.logDataOperation(receiver.getId(), OP_AGGREGATE);
            receiver.consumeEnergy(energyDa); // consumeEnergy handles death check
        }
    }


    private void finalizeRoundMetrics(int roundNumber) {
        // Update overall node statuses and count alive nodes
        long aliveCount = 0;
        double totalRemainingNetworkEnergy = 0;
        int deadNodesThisRound = 0;

        for (SensorNode node : this.nodes) {
            // The consumeEnergy method already updates status if energy hits zero.
            // Here we just count.
            if (node.isAlive()) {
                aliveCount++;
                totalRemainingNetworkEnergy += node.getEnergy();
            } else {
                // Check if it just died in this round for FND logic (if needed, but consumeEnergy handles status)
            }
        }

        metrics.logAliveNodes((int) aliveCount);
        metrics.logTotalRemainingEnergy(totalRemainingNetworkEnergy);
        metrics.logEnergyConsumedThisRound(this.currentRoundTotalEnergyConsumed);

        // Log FND, HND, LND
        if (metrics.getFndRound() == -1 && aliveCount < this.nodes.size()) {
            metrics.setFndRound(roundNumber);
        }
        // HND check (can be refined based on exact definition: <= 50% initial or < 50% initial)
        if (metrics.getHndRound() == -1 && aliveCount <= (double) PEGASISConfig.NUM_NODES / 2.0) {
             metrics.setHndRound(roundNumber);
        }
        if (aliveCount == 0 && metrics.getLndRound() == -1) {
            metrics.setLndRound(roundNumber);
        }
    }
}