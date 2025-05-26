# File: analyze_results.py
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns # For better aesthetics on some plots
import numpy as np # For handling 'N/A' as NaN for numeric conversion

# --- Configuration ---
# Adjust these filenames if your runSuffix resulted in different names
STD_PEGASIS_ROUND_FILE = 'StandardPEGASIS_StandardPegasisRun_round_data.csv'
HALEM_PEGASIS_ROUND_FILE = 'HALEM-PEGASIS_HALEMPegasisRun_round_data.csv'
SUMMARY_FILE = 'simulation_summary.csv'

# --- Plotting Function for Round-by-Round Data (Existing) ---
def plot_round_by_round_comparison(df_std, df_halem, std_label='Standard PEGASIS', halem_label='HALEM-PEGASIS'):
    """Plots round-by-round comparisons for various metrics."""
    plt.style.use('seaborn-v0_8-whitegrid')

    # 1. Alive Nodes vs. Rounds
    plt.figure(figsize=(10, 6))
    if not df_std.empty and 'Round' in df_std.columns and 'AliveNodes' in df_std.columns:
        plt.plot(df_std['Round'], df_std['AliveNodes'], label=f'{std_label} - Alive Nodes', linestyle='--')
    if not df_halem.empty and 'Round' in df_halem.columns and 'AliveNodes' in df_halem.columns:
        plt.plot(df_halem['Round'], df_halem['AliveNodes'], label=f'{halem_label} - Alive Nodes', linestyle='-')
    plt.xlabel('Round Number')
    plt.ylabel('Number of Alive Nodes')
    plt.title('Network Survivability: Alive Nodes over Time')
    plt.legend()
    plt.grid(True)
    plt.savefig('plot_alive_nodes.png')
    # plt.show() # Show plots at the end or individually

    # 2. Total Remaining Energy vs. Rounds
    plt.figure(figsize=(10, 6))
    if not df_std.empty and 'Round' in df_std.columns and 'TotalRemainingEnergy' in df_std.columns:
        plt.plot(df_std['Round'], df_std['TotalRemainingEnergy'], label=f'{std_label} - Remaining Energy', linestyle='--')
    if not df_halem.empty and 'Round' in df_halem.columns and 'TotalRemainingEnergy' in df_halem.columns:
        plt.plot(df_halem['Round'], df_halem['TotalRemainingEnergy'], label=f'{halem_label} - Remaining Energy', linestyle='-')
    plt.xlabel('Round Number')
    plt.ylabel('Total Remaining Energy (J)')
    plt.title('Network Energy Depletion')
    plt.legend()
    plt.grid(True)
    plt.savefig('plot_remaining_energy.png')
    # plt.show()

    # 3. Energy Consumed Per Round vs. Rounds (Optional - can be noisy)
    if ('EnergyConsumedThisRound' in df_std.columns and not df_std.empty) or \
       ('EnergyConsumedThisRound' in df_halem.columns and not df_halem.empty):
        plt.figure(figsize=(10, 6))
        if not df_std.empty and 'Round' in df_std.columns and 'EnergyConsumedThisRound' in df_std.columns:
            plt.plot(df_std['Round'], df_std['EnergyConsumedThisRound'], label=f'{std_label} - Energy/Round', alpha=0.7, linestyle=':')
        if not df_halem.empty and 'Round' in df_halem.columns and 'EnergyConsumedThisRound' in df_halem.columns:
            plt.plot(df_halem['Round'], df_halem['EnergyConsumedThisRound'], label=f'{halem_label} - Energy/Round', alpha=0.7, linestyle=':')
        plt.xlabel('Round Number')
        plt.ylabel('Energy Consumed in Round (J)')
        plt.title('Per-Round Energy Consumption')
        plt.legend()
        plt.grid(True)
        plt.savefig('plot_energy_per_round.png')
        # plt.show()

# --- Plotting Function for Summary Data (Existing) ---
def plot_summary_comparison(df_summary):
    """Plots bar charts for summary metrics."""
    if df_summary.empty:
        print("Summary data is empty, skipping summary plots.")
        return

    plt.style.use('seaborn-v0_8-whitegrid')

    df_summary_plot = df_summary.copy()
    for col in ['FND_Round', 'HND_Round', 'LND_Round', 'TotalRoundsSimulated']:
        if col in df_summary_plot.columns:
            df_summary_plot[col] = pd.to_numeric(df_summary_plot[col], errors='coerce')

    if 'TotalRoundsSimulated' in df_summary_plot.columns:
        max_rounds_overall = df_summary_plot['TotalRoundsSimulated'].max()
        if pd.notna(max_rounds_overall):
            for col in ['HND_Round', 'LND_Round']: # FND should ideally always have a value if any node dies
                if col in df_summary_plot.columns:
                    df_summary_plot[col] = df_summary_plot[col].fillna(max_rounds_overall + (max_rounds_overall * 0.05))


    # 1. Network Lifetime Events
    lifetime_metrics_present = [col for col in ['FND_Round', 'HND_Round', 'LND_Round'] if col in df_summary_plot.columns]
    if lifetime_metrics_present:
        df_lifetime = df_summary_plot[['ProtocolName'] + lifetime_metrics_present]
        df_lifetime_melted = df_lifetime.melt(id_vars='ProtocolName', var_name='Lifetime Event', value_name='Round')
        plt.figure(figsize=(10, 7)) # Adjusted size
        sns.barplot(x='Lifetime Event', y='Round', hue='ProtocolName', data=df_lifetime_melted, palette='viridis')
        plt.title('Network Lifetime Milestones')
        plt.ylabel('Round Number')
        plt.xlabel('Lifetime Event')
        plt.xticks(rotation=30, ha="right")
        plt.legend(title='Protocol')
        plt.tight_layout()
        plt.savefig('plot_summary_lifetime.png')
        # plt.show()

    # 2. Total Data Delivered
    if 'TotalDataDeliveredToBS_bits' in df_summary_plot.columns:
        plt.figure(figsize=(8, 6))
        sns.barplot(x='ProtocolName', y='TotalDataDeliveredToBS_bits', data=df_summary_plot, palette='coolwarm', hue='ProtocolName', dodge=False)
        plt.title('Total Data Delivered to Sink')
        plt.ylabel('Data (bits)')
        plt.xlabel('Protocol')
        plt.xticks(rotation=30, ha="right")
        plt.legend([],[], frameon=False) # Hide redundant legend if using hue for single bars
        plt.tight_layout()
        plt.savefig('plot_summary_data_delivered.png')
        # plt.show()

    # 3. Total Energy Consumed
    if 'TotalEnergyConsumed_AllNodes_J' in df_summary_plot.columns:
        plt.figure(figsize=(8, 6))
        sns.barplot(x='ProtocolName', y='TotalEnergyConsumed_AllNodes_J', data=df_summary_plot, palette='autumn', hue='ProtocolName', dodge=False)
        plt.title('Total Network Energy Consumption')
        plt.ylabel('Energy (Joules)')
        plt.xlabel('Protocol')
        plt.xticks(rotation=30, ha="right")
        plt.legend([],[], frameon=False)
        plt.tight_layout()
        plt.savefig('plot_summary_total_energy.png')
        # plt.show()
        
    # 4. Average Execution Time Per Round
    if 'AvgRoundExecTime_ms' in df_summary_plot.columns:
        plt.figure(figsize=(8, 6))
        sns.barplot(x='ProtocolName', y='AvgRoundExecTime_ms', data=df_summary_plot, palette='winter', hue='ProtocolName', dodge=False)
        plt.title('Average Execution Time per Round')
        plt.ylabel('Time (ms)')
        plt.xlabel('Protocol')
        plt.xticks(rotation=30, ha="right")
        plt.legend([],[], frameon=False)
        plt.tight_layout()
        plt.savefig('plot_summary_avg_exec_time.png')
        # plt.show()

# --- NEW: Enhanced Summary Table Function ---
def print_enhanced_summary_table(df_summary_orig):
    """Prints an enhanced summary table and calculates percentage differences."""
    if df_summary_orig.empty or len(df_summary_orig) < 2:
        print("\nNot enough data (protocols) in summary for a comparative table.")
        if not df_summary_orig.empty:
            print("\n--- Single Protocol Summary ---")
            print(df_summary_orig.to_string())
        return

    df_summary = df_summary_orig.copy() # Work on a copy

    # Assuming the first row is baseline (e.g., Standard PEGASIS)
    # and second is the protocol to compare (e.g., HALEM-PEGASIS)
    # This logic might need adjustment if you have more than 2 protocols or different order
    baseline_protocol_name = df_summary['ProtocolName'].iloc[0]
    compared_protocol_name = df_summary['ProtocolName'].iloc[1]
    
    std_metrics = df_summary.iloc[0]
    halem_metrics = df_summary.iloc[1]

    print("\n\n--- Enhanced Comparative Summary Table ---")
    header = f"{'Metric':<35} | {baseline_protocol_name:<25} | {compared_protocol_name:<25} | {'% Change (' + compared_protocol_name[0:5] + ' vs ' + baseline_protocol_name[0:3] + ')':<28}"
    print(header)
    print("-" * len(header))

    def get_change_str(std_val, halem_val, higher_is_better=True):
        s_val_num = pd.to_numeric(std_val, errors='coerce')
        h_val_num = pd.to_numeric(halem_val, errors='coerce')

        if pd.isna(s_val_num) or pd.isna(h_val_num):
            if pd.isna(s_val_num) and pd.isna(h_val_num): return "Both N/A"
            if pd.isna(s_val_num): return f"{halem_val} (Baseline N/A)"
            if pd.isna(h_val_num): return f"{std_val} (Compared N/A)"
            return "N/A (one value missing)"
        
        if abs(s_val_num) < 1e-9 : # Avoid division by zero or very small number
            return "N/A (Baseline zero)" if h_val_num == 0 else f"{h_val_num:.2f} (Baseline zero)"

        change = ((h_val_num - s_val_num) / abs(s_val_num)) * 100
        change_str = f"{change:+.2f}%"
        
        if change == 0:
             change_str += " (No change)"
        elif (change > 0 and higher_is_better) or (change < 0 and not higher_is_better):
            change_str += " (Better)"
        else:
            change_str += " (Worse)"
        return change_str

    metrics_to_compare = [
        ("Total Rounds Simulated", 'TotalRoundsSimulated', True),
        ("FND Round", 'FND_Round', True),
        ("HND Round", 'HND_Round', True),
        ("LND Round", 'LND_Round', True),
        ("Total BS Transmissions", 'TotalSuccessfulTransmissionsToBS', True),
        ("Total Data Delivered (bits)", 'TotalDataDeliveredToBS_bits', True),
        ("Total Energy Consumed (J)", 'TotalEnergyConsumed_AllNodes_J', False),
        ("Avg Energy/Round (J)", 'AvgEnergyPerRound_J', False),
        ("Avg Round Exec Time (ms)", 'AvgRoundExecTime_ms', False),
        ("Avg Chain Formation Time (ms)", 'AvgChainFormationTime_ms', False)
    ]

    for name, key, higher_better in metrics_to_compare:
        if key in std_metrics and key in halem_metrics:
            s_val = std_metrics[key]
            h_val = halem_metrics[key]
            change_display = get_change_str(s_val, h_val, higher_better)
            # Format numeric values for display, keep N/A as is
            s_val_disp = f"{pd.to_numeric(s_val, errors='ignore'):.2f}" if pd.api.types.is_number(s_val) and not pd.isna(s_val) else str(s_val)
            h_val_disp = f"{pd.to_numeric(h_val, errors='ignore'):.2f}" if pd.api.types.is_number(h_val) and not pd.isna(h_val) else str(h_val)
            if "Energy" in name and "Avg" not in name: # More precision for total energy
                 s_val_disp = f"{pd.to_numeric(s_val, errors='ignore'):.4f}" if pd.api.types.is_number(s_val) and not pd.isna(s_val) else str(s_val)
                 h_val_disp = f"{pd.to_numeric(h_val, errors='ignore'):.4f}" if pd.api.types.is_number(h_val) and not pd.isna(h_val) else str(h_val)
            if "Avg Energy" in name :
                 s_val_disp = f"{pd.to_numeric(s_val, errors='ignore'):.6f}" if pd.api.types.is_number(s_val) and not pd.isna(s_val) else str(s_val)
                 h_val_disp = f"{pd.to_numeric(h_val, errors='ignore'):.6f}" if pd.api.types.is_number(h_val) and not pd.isna(h_val) else str(h_val)


            print(f"{name:<35} | {s_val_disp:<25} | {h_val_disp:<25} | {change_display:<28}")
        else:
            print(f"{name:<35} | {'Metric N/A':<25} | {'Metric N/A':<25} | ")
    print("-" * len(header))


# --- NEW: Cumulative Energy Consumption Plot Function ---
def plot_cumulative_energy(df_std, df_halem, std_label='Standard PEGASIS', halem_label='HALEM-PEGASIS'):
    """Plots cumulative energy consumption over rounds."""
    if df_std.empty and df_halem.empty:
        print("No round data for cumulative energy plot.")
        return

    plt.figure(figsize=(10, 6))
    plt.style.use('seaborn-v0_8-whitegrid')

    if not df_std.empty and 'Round' in df_std.columns and 'EnergyConsumedThisRound' in df_std.columns:
        df_std['CumulativeEnergy'] = df_std['EnergyConsumedThisRound'].cumsum()
        plt.plot(df_std['Round'], df_std['CumulativeEnergy'], label=f'{std_label} - Cumulative Energy', linestyle='--')
    
    if not df_halem.empty and 'Round' in df_halem.columns and 'EnergyConsumedThisRound' in df_halem.columns:
        df_halem['CumulativeEnergy'] = df_halem['EnergyConsumedThisRound'].cumsum()
        plt.plot(df_halem['Round'], df_halem['CumulativeEnergy'], label=f'{halem_label} - Cumulative Energy', linestyle='-')
    
    plt.xlabel('Round Number')
    plt.ylabel('Cumulative Energy Consumed (J)')
    plt.title('Cumulative Network Energy Consumption')
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig('plot_cumulative_energy.png')
    # plt.show()

# --- NEW: Node Death Rate Plot Function ---
def plot_node_death_rate(df_std, df_halem, std_label='Standard PEGASIS', halem_label='HALEM-PEGASIS', window=50):
    """Plots the rate of node death (nodes dying per window of rounds)."""
    if df_std.empty and df_halem.empty:
        print("No round data for node death rate plot.")
        return

    plt.figure(figsize=(10, 6))
    plt.style.use('seaborn-v0_8-whitegrid')

    if not df_std.empty and 'Round' in df_std.columns and 'AliveNodes' in df_std.columns:
        # Calculate nodes died in a sliding window
        df_std['NodesDiedInWindow'] = -df_std['AliveNodes'].diff(periods=window).fillna(0)
        df_std.loc[df_std['NodesDiedInWindow'] < 0, 'NodesDiedInWindow'] = 0 # Ensure no negative deaths if nodes revive (not in this sim)
        plt.plot(df_std['Round'], df_std['NodesDiedInWindow'], label=f'{std_label} - Nodes Died / {window} Rnds', linestyle='--')

    if not df_halem.empty and 'Round' in df_halem.columns and 'AliveNodes' in df_halem.columns:
        df_halem['NodesDiedInWindow'] = -df_halem['AliveNodes'].diff(periods=window).fillna(0)
        df_halem.loc[df_halem['NodesDiedInWindow'] < 0, 'NodesDiedInWindow'] = 0
        plt.plot(df_halem['Round'], df_halem['NodesDiedInWindow'], label=f'{halem_label} - Nodes Died / {window} Rnds', linestyle='-')

    plt.xlabel('Round Number')
    plt.ylabel(f'Nodes Died in Previous {window} Rounds')
    plt.title('Rate of Node Death')
    plt.legend()
    plt.grid(True)
    plt.ylim(bottom=0) # Ensure y-axis starts at 0 for death count
    plt.tight_layout()
    plt.savefig('plot_node_death_rate.png')
    # plt.show()

# --- Main Execution Block ---
if __name__ == '__main__':
    # Load data
    try:
        df_std_rounds = pd.read_csv(STD_PEGASIS_ROUND_FILE)
        print(f"Successfully loaded: {STD_PEGASIS_ROUND_FILE}")
    except FileNotFoundError:
        print(f"Error: File not found - {STD_PEGASIS_ROUND_FILE}. Please check filename and path.")
        df_std_rounds = pd.DataFrame() 

    try:
        df_halem_rounds = pd.read_csv(HALEM_PEGASIS_ROUND_FILE)
        print(f"Successfully loaded: {HALEM_PEGASIS_ROUND_FILE}")
    except FileNotFoundError:
        print(f"Error: File not found - {HALEM_PEGASIS_ROUND_FILE}. Please check filename and path.")
        df_halem_rounds = pd.DataFrame()

    try:
        df_summary = pd.read_csv(SUMMARY_FILE)
        print(f"Successfully loaded: {SUMMARY_FILE}")
    except FileNotFoundError:
        print(f"Error: File not found - {SUMMARY_FILE}. Please check filename and path.")
        df_summary = pd.DataFrame()

    # Determine labels for plots
    std_label_from_summary = "Standard PEGASIS"
    halem_label_from_summary = "HALEM-PEGASIS"

    if not df_summary.empty and 'ProtocolName' in df_summary.columns:
        protocol_names_in_summary = df_summary['ProtocolName'].unique()
        if len(protocol_names_in_summary) > 0:
            # Attempt to match filenames to protocol names for more accurate labels
            # This is a basic match, might need refinement if filenames are very different
            for name in protocol_names_in_summary:
                if STD_PEGASIS_ROUND_FILE.lower().startswith(name.lower().replace(" ", "").replace("-", "")):
                    std_label_from_summary = name
                if HALEM_PEGASIS_ROUND_FILE.lower().startswith(name.lower().replace(" ", "").replace("-", "")):
                    halem_label_from_summary = name
        if len(protocol_names_in_summary) == 1: # If only one protocol in summary
            if STD_PEGASIS_ROUND_FILE.lower().startswith(protocol_names_in_summary[0].lower().replace(" ", "").replace("-", "")):
                 std_label_from_summary = protocol_names_in_summary[0]
            if HALEM_PEGASIS_ROUND_FILE.lower().startswith(protocol_names_in_summary[0].lower().replace(" ", "").replace("-", "")):
                 halem_label_from_summary = protocol_names_in_summary[0]


    print(f"\nUsing labels: '{std_label_from_summary}' and '{halem_label_from_summary}' for plots.")

    # Generate plots and tables
    if not df_std_rounds.empty or not df_halem_rounds.empty:
        print("\nGenerating round-by-round comparison plots...")
        plot_round_by_round_comparison(df_std_rounds, df_halem_rounds, std_label_from_summary, halem_label_from_summary)
        plot_cumulative_energy(df_std_rounds.copy(), df_halem_rounds.copy(), std_label_from_summary, halem_label_from_summary)
        plot_node_death_rate(df_std_rounds.copy(), df_halem_rounds.copy(), std_label_from_summary, halem_label_from_summary, window=50)
    else:
        print("No round-by-round data loaded. Skipping these comparison plots.")

    if not df_summary.empty:
        print("\nGenerating summary comparison plots and table...")
        plot_summary_comparison(df_summary)
        print_enhanced_summary_table(df_summary) # Call the new table function
    else:
        print("No summary data loaded. Skipping summary plots and table.")
    
    print("\nPython analysis script finished. Check for generated .png plot files and console table.")
    print("If plots did not display automatically, open the .png files in your project directory.")
    plt.show() # Call show once at the end to display all generated figures if not shown individually