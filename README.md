# HALEM-PEGASIS Evaluator

> A simulation and evaluation tool for comparing the standard PEGASIS protocol with the novel HALEM-PEGASIS protocol in Wireless Sensor Networks (WSNs). Developed in Java with a Swing-based GUI.

---

## 📌 About the Project

This tool allows users to simulate and analyze two routing protocols for WSNs:

1. **PEGASIS (Power-Efficient Gathering in Sensor Information System)** – a chain-based protocol that minimizes energy use by forming a chain of sensor nodes.
2. **HALEM-PEGASIS** – a novel enhancement of PEGASIS incorporating:
   - **Hierarchical multi-chain formation**
   - **Adaptive leader selection using residual energy & centrality**
   - **Latency-energy tradeoff handling for QoS**
   - **Support for mobile sinks**
   - **Dynamic load balancing**

The tool offers an interactive simulation platform where protocol behaviors can be observed, measured, and compared under various WSN configurations.

---

## 🧪 Features

- ✅ Visual chain formation with sensor nodes
- ✅ Residual energy-based leader rotation
- ✅ Hierarchical clustering and chain merging
- ✅ Time complexity and energy consumption analysis
- ✅ Support for both static and mobile sink simulations
- ✅ Comparison tables with key metrics

---

## 🛠️ Technologies Used

- Java (JDK 17+)
- Java Swing (GUI)
- Object-Oriented Design
- Maven (for build and dependency management)

---

## 📊 Performance Metrics Compared

| Metric                  | PEGASIS           | HALEM-PEGASIS      |
|-------------------------|-------------------|--------------------|
| Energy Efficiency       | Medium            | High               |
| Latency                 | High              | Low (QoS-aware)    |
| Network Lifetime        | Moderate          | Extended           |
| Scalability             | Limited           | High (Hierarchical)|
| Time Complexity         | `O(n)`            | `O(n log k)`       |
| Load Balancing          | Minimal           | Dynamic & Fair     |
| Sink Mobility Support   | No                | Yes                |

