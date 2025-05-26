// File: PEGASISConfig.java
public class PEGASISConfig {
    // --- Energy Parameters ---
    public static final double E_ELEC = 50e-9;  // Radio electronics energy (J/bit)
    public static final double E_AMP = 100e-12; // Transmit amplifier energy (J/bit/m^2)
    public static final double E_DA = 5e-9;     // Data aggregation energy (J/bit/signal)
    public static double INITIAL_ENERGY = 0.5;  // Initial energy per node (Joules)

    // --- Packet Sizes ---
    public static final int DATA_PACKET_SIZE_BITS = 2000; // Size of a data packet in bits
    // Assuming zone leaders send same size packet, or super leader aggregates further.
    // For simplicity, let's assume each zone leader sends one DATA_PACKET_SIZE_BITS packet
    // and super leader sends one such packet too after aggregation.
    public static final int CONTROL_PACKET_SIZE_BITS = 100;

    // --- Network & Simulation Parameters (Defaults) ---
    public static int NUM_NODES = 100;
    public static double AREA_WIDTH = 100.0;
    public static double AREA_HEIGHT = 100.0;
    public static int MAX_ROUNDS = 2000;

    // --- Base Station / Sink Parameters (Defaults) ---
    public static double BS_X = 50.0;
    public static double BS_Y = 175.0;
    public static boolean IS_SINK_MOBILE = true;
    public static double SINK_SPEED_PER_ROUND = 1.0;
    public static int SINK_MOVEMENT_PATTERN = 1; // 1 for horizontal linear
    public static double SINK_MIN_X = 0.0;
    public static double SINK_MAX_X = AREA_WIDTH;
    public static double SINK_MIN_Y = BS_Y;
    public static double SINK_MAX_Y = BS_Y;

    // --- Standard PEGASIS Specific Parameters ---
    public static boolean REFORM_CHAIN_ON_DEATH_PEGASIS = true;
    public static int REFORM_CHAIN_INTERVAL_PEGASIS = 25;

    // --- HALEM-PEGASIS Specific Parameters ---
    public static int HALEM_ZONE_ROWS = 2;
    public static int HALEM_ZONE_COLS = 2;
    public static double HALEM_LEADER_SCORE_W1_ENERGY = 0.6;
    public static double HALEM_LEADER_SCORE_W2_CENTRALITY = 0.4;
    public static int HALEM_LEADER_COOLDOWN_ROUNDS = 10;
    public static boolean HALEM_ENABLE_BACKUP_LEADERS = true;

    public static boolean HALEM_ENABLE_UPPER_HIERARCHY = true; // New flag
    // If a zone leader is farther than this from the sink, it prefers the upper hierarchy.
    public static double HALEM_SINK_DISTANCE_THRESHOLD_FOR_HIERARCHY = 75.0; // meters
    // If fewer than this many leaders opt for hierarchy, they all go direct to sink anyway.
    public static int HALEM_MIN_LEADERS_FOR_UPPER_CHAIN = 2;


    // --- Output/Logging ---
    public static final String SUMMARY_CSV_FILENAME = "simulation_summary.csv";
    public static final String ROUND_DATA_CSV_POSTFIX = "_round_data.csv";

    public static void parseArgs(String[] args) {
        try {
            if (args.length >= 1) NUM_NODES = Integer.parseInt(args[0]);
            if (args.length >= 2) {
                AREA_WIDTH = Double.parseDouble(args[1]);
                AREA_HEIGHT = Double.parseDouble(args[1]);
                SINK_MAX_X = AREA_WIDTH;
            }
            if (args.length >= 3) MAX_ROUNDS = Integer.parseInt(args[2]);
            if (args.length >= 4) INITIAL_ENERGY = Double.parseDouble(args[3]);
            if (args.length >= 5) BS_X = Double.parseDouble(args[4]);
            if (args.length >= 6) {
                BS_Y = Double.parseDouble(args[5]);
                SINK_MIN_Y = BS_Y; SINK_MAX_Y = BS_Y;
            }
            // Add parsing for new HALEM flags if desired, e.g.:
            // if (args.length >= 7) HALEM_ENABLE_UPPER_HIERARCHY = Boolean.parseBoolean(args[6]);
            // if (args.length >= 8) HALEM_SINK_DISTANCE_THRESHOLD_FOR_HIERARCHY = Double.parseDouble(args[7]);

        } catch (NumberFormatException e) {
            System.err.println("Error parsing command line arguments. Using defaults. " + e.getMessage());
        }

        System.out.println("--- Simulation Configuration Initialized ---");
        System.out.println("Nodes: " + NUM_NODES + ", Area: " + AREA_WIDTH + "x" + AREA_HEIGHT + "m, Max Rounds: " + MAX_ROUNDS);
        System.out.println("Initial Energy: " + INITIAL_ENERGY + "J, BS Location: (" + BS_X + "," + BS_Y + ")");
        System.out.println("Sink Mobile: " + IS_SINK_MOBILE +
                           (IS_SINK_MOBILE ? ", Speed: " + SINK_SPEED_PER_ROUND + "m/round, Pattern: " + SINK_MOVEMENT_PATTERN : ""));
        System.out.println("HALEM Zones: " + HALEM_ZONE_ROWS + "x" + HALEM_ZONE_COLS +
                           ", Backup Leaders: " + HALEM_ENABLE_BACKUP_LEADERS +
                           ", Upper Hierarchy: " + HALEM_ENABLE_UPPER_HIERARCHY +
                           (HALEM_ENABLE_UPPER_HIERARCHY ? ", HierThreshold: " + HALEM_SINK_DISTANCE_THRESHOLD_FOR_HIERARCHY + "m" : ""));
        System.out.println("------------------------------------------");
    }
}