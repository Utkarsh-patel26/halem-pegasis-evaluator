// File: Protocol.java
import java.util.List;
import java.util.Map;
import java.util.Random;

public interface Protocol {
    /**
     * Initializes/sets up the protocol with a fresh list of nodes, base station,
     * metrics collector, and a random seed for any protocol-specific randomization.
     * Node deployment (positions) should be handled externally to ensure fairness
     * if multiple protocols use the same initial deployment.
     *
     * @param initialNodes List of newly initialized sensor nodes for this run.
     * @param bs The base station.
     * @param mc The metrics collector for this run.
     * @param protocolRandomSeed A random number generator for protocol-internal stochastic processes.
     */
    void setup(List<SensorNode> initialNodes, BaseStation bs, MetricsCollector mc, Random protocolRandomSeed);

    /**
     * Executes a single round of communication for the protocol.
     *
     * @param roundNumber The current simulation round number.
     * @return A map containing key statistics or events from this round (e.g., leader ID, errors).
     */
    Map<String, Object> runSingleRound(int roundNumber);

    /**
     * @return The current count of nodes considered alive by the protocol.
     */
    long getAliveNodesCount();

    /**
     * Provides access to the list of sensor nodes managed by this protocol instance.
     * Useful for fetching final node states for detailed metrics.
     * @return The list of sensor nodes.
     */
    List<SensorNode> getNodes();

    /**
     * @return A descriptive name for this protocol implementation.
     */
    String getProtocolName();
}