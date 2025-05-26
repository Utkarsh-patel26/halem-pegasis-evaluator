// File: BaseStation.java (Mobile-Capable Sink)
public class BaseStation {
    private final int id;
    private double initialX; // Store initial position
    private double initialY;
    private double currentX; // Current position, can change if mobile
    private double currentY;
    private long totalDataBitsReceived;
    private long totalPacketsReceived;

    // For simple linear movement pattern
    private boolean movingRight = true;

    public BaseStation(int id, double x, double y) {
        this.id = id;
        this.initialX = x;
        this.initialY = y;
        this.currentX = x; // Initially at the specified x, y
        this.currentY = y;
        this.totalDataBitsReceived = 0;
        this.totalPacketsReceived = 0;
    }

    // Getters for CURRENT position
    public double getX() {
        return currentX;
    }

    public double getY() {
        return currentY;
    }

    public long getTotalDataBitsReceived() {
        return totalDataBitsReceived;
    }

    public long getTotalPacketsReceived() {
        return totalPacketsReceived;
    }

    /**
     * Simulates receiving data from a sensor node.
     * @param sender The node sending the data.
     * @param dataSizeBits The size of the data packet in bits.
     * @param metrics A MetricsCollector instance to log the successful transmission.
     */
    public void receiveData(SensorNode sender, int dataSizeBits, MetricsCollector metrics) {
        this.totalDataBitsReceived += dataSizeBits;
        this.totalPacketsReceived++;
        if (metrics != null) {
            metrics.incrementSuccessfulTransmissionsToBS();
            metrics.addDataDeliveredToBS(dataSizeBits);
        }
        // Optional: Debug print
        // System.out.println("Sink received " + dataSizeBits + " bits from Node " + sender.getId());
    }

    /**
     * Moves the sink based on the configured pattern and speed in PEGASISConfig.
     * This method should be called once per round by the SimulationRunner if IS_SINK_MOBILE is true.
     */
    public void move() {
        if (!PEGASISConfig.IS_SINK_MOBILE) {
            return; // Do not move if mobility is disabled
        }

        switch (PEGASISConfig.SINK_MOVEMENT_PATTERN) {
            case 1: // Horizontal Linear Movement (back and forth)
                if (movingRight) {
                    currentX += PEGASISConfig.SINK_SPEED_PER_ROUND;
                    if (currentX >= PEGASISConfig.SINK_MAX_X) {
                        currentX = PEGASISConfig.SINK_MAX_X;
                        movingRight = false;
                    }
                } else { // Moving left
                    currentX -= PEGASISConfig.SINK_SPEED_PER_ROUND;
                    if (currentX <= PEGASISConfig.SINK_MIN_X) {
                        currentX = PEGASISConfig.SINK_MIN_X;
                        movingRight = true;
                    }
                }
                // For this horizontal pattern, Y coordinate remains fixed based on initial setup or SINK_MIN_Y
                currentY = PEGASISConfig.SINK_MIN_Y;
                break;
            case 2: // Vertical Linear Movement (example, implement if needed)
                // Add similar logic for currentY, moving between SINK_MIN_Y and SINK_MAX_Y
                // currentX would be fixed.
                System.err.println("Warning: Vertical sink movement pattern not fully implemented in BaseStation.move().");
                break;
            // case 3: // Circular Movement (example, implement if needed)
            //    break;
            default: // Static or unknown pattern
                // No movement, currentX and currentY remain unchanged
                break;
        }
    }

    /**
     * Resets the sink to its initial state (position and received data counts).
     */
    public void reset() {
        this.currentX = this.initialX;
        this.currentY = this.initialY;
        this.totalDataBitsReceived = 0;
        this.totalPacketsReceived = 0;
        this.movingRight = true; // Reset movement direction for linear pattern
    }

    @Override
    public String toString() {
        return String.format("Sink[%d] (Current Pos: %.2f, %.2f) RxPackets:%d RxBits:%d",
                id, currentX, currentY, totalPacketsReceived, totalDataBitsReceived);
    }
}