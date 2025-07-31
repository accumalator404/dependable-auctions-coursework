# Fault-Tolerant Auction System

A distributed auction system implementing passive replication and automatic failover mechanisms using Java RMI.

## What I Built

### Distributed Auction System with Replication
- **Multi-Replica Architecture**: Built auction system with multiple server replicas for reliability
- **Stateless Front-End**: Created load balancer that routes client requests to active replica
- **Data Synchronization**: Ensured all replicas maintain consistent auction state
- **Primary-Backup Design**: Any replica can serve as the primary server

```java
// Start replica servers with unique IDs
java Replica 1
java Replica 2
java Replica 3

// Front-end automatically manages which replica is primary
int primaryID = frontend.getPrimaryReplicaID();
```

### Fault Tolerance and Recovery
- **Automatic Failover**: System continues working when servers crash
- **Complete Server Replacement**: Can replace all original servers while system runs
- **Seamless Recovery**: Crashed servers rejoin and sync automatically
- **Smart Failure Detection**: Detects failures during normal operations (no constant polling)

### Core Auction Features
- **User Registration**: Email-based user accounts
- **Auction Management**: Create, list, and close auctions
- **Real-time Bidding**: Place bids with validation
- **Winner Determination**: Process auction results

## Key Skills Demonstrated

- **Distributed Systems**: Replication, consistency, fault tolerance
- **Java Programming**: RMI, multi-threading, process management
- **System Architecture**: Load balancing, failover design
- **Reliability Engineering**: Building systems that handle real failures

## Technologies Used

- Java RMI for distributed communication
- Multi-process architecture
- Concurrent request handling
- Fault detection and recovery mechanisms

## System Architecture

```
Client → Front-End Load Balancer → Primary Replica
                                 ↓
                           Backup Replicas
                           (Stay Synchronized)
```

When primary fails, front-end automatically promotes a backup to primary.

## Running the System

```bash
# Start entire system (front-end + replicas)
./server.sh

# System starts:
# - Front-end service accessible via RMI
# - Multiple replica processes
# - Ready within 5 seconds
```

## Fault Tolerance Capabilities

The system handles these real-world scenarios:
- ✅ Primary server crashes during active bidding
- ✅ Multiple servers fail simultaneously 
- ✅ All original servers replaced with new ones
- ✅ Servers recover and rejoin automatically
- ✅ Network communication failures

## Design Highlights

- **No Polling**: Detects failures reactively during normal operation
- **Fast Recovery**: Crashed servers sync automatically when restarting
- **Scalable**: Efficient design that works with many replicas
- **Consistent**: All replicas maintain identical auction data

This project demonstrates building production-ready distributed systems with enterprise-level reliability and fault tolerance.
