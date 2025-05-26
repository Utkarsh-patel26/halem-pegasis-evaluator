// File: SensorNode.java
import java.util.Objects;

public class SensorNode {
    public enum Status { ALIVE, DEAD }
    public enum Role { UNASSIGNED, FOLLOWER, LEADER } // Basic roles for PEGASIS

    private final int id;
    private final double x;
    private final double y;
    private double energy;
    private final double initialEnergy;
    private Status status;
    private Role role;

    // For metrics and advanced protocols later
    private int packetsSent;
    private int packetsReceived;
    private int timesAsLeader;

    public SensorNode(int id, double x, double y, double initialEnergy) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.initialEnergy = initialEnergy;
        this.energy = initialEnergy;
        this.status = Status.ALIVE;
        this.role = Role.UNASSIGNED;
        this.packetsSent = 0;
        this.packetsReceived = 0;
        this.timesAsLeader = 0;
    }

    // --- Getters ---
    public int getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getEnergy() { return energy; }
    public double getInitialEnergy() { return initialEnergy; }
    public Status getStatus() { return status; }
    public Role getRole() { return role; }
    public boolean isAlive() { return status == Status.ALIVE; }
    public int getPacketsSent() { return packetsSent; }
    public int getPacketsReceived() { return packetsReceived; }
    public int getTimesAsLeader() { return timesAsLeader; }


    // --- Setters ---
    public void setRole(Role role) { this.role = role; }
    // Status is primarily changed via energy depletion logic

    // --- Energy Consumption & State Update ---
    /**
     * Consumes a specified amount of energy. Updates status if energy depleted.
     * @param amount Energy amount to consume.
     * @return true if still alive after consumption, false otherwise.
     */
    public boolean consumeEnergy(double amount) {
        if (!isAlive()) return false;

        this.energy -= amount;
        if (this.energy <= 0) {
            this.energy = 0;
            this.status = Status.DEAD;
            // System.out.println("Node " + id + " died."); // Optional: for debugging
            return false;
        }
        return true;
    }

    // --- Packet Tracking (for metrics) ---
    public void incrementPacketsSent() { this.packetsSent++; }
    public void incrementPacketsReceived() { this.packetsReceived++; }
    public void incrementTimesAsLeader() { this.timesAsLeader++; }

    // --- Utility ---
    public void reset() {
        this.energy = this.initialEnergy;
        this.status = Status.ALIVE;
        this.role = Role.UNASSIGNED;
        this.packetsSent = 0;
        this.packetsReceived = 0;
        this.timesAsLeader = 0;
    }

    @Override
    public String toString() {
        return String.format("Node[%d] (%.2f, %.2f) E:%.4f S:%s R:%s",
                id, x, y, energy, status, role);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SensorNode that = (SensorNode) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}