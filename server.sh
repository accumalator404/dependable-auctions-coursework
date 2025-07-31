
# Kill any existing processes first
pkill -f "java Replica"
rm -f client/*.dat
sleep 0.5

# Change to server directory
cd server

pkill rmiregistry &
sleep 0.5
# Start RMI registry in server directory
rmiregistry &
sleep 1

# Start replicas from server directory
java Replica 1 &
java Replica 2 &
java Replica 3 &
sleep 0.5

# Start frontend
java FrontEnd 