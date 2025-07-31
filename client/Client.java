import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

public class Client {
    private static int userID = -1;
    private static String currentEmail = null;
    private static Auction server;
    private static final String USER_FILE_PREFIX = "user_";

    private static String getUserFile(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email cannot be null");
        }
        return USER_FILE_PREFIX + email.replaceAll("[^a-zA-Z0-9]", "_") + ".dat";
    }

    private static void loadUserData(String email) {
        if (email == null)
            return;

        String userFile = getUserFile(email);
        try {
            if (new File(userFile).exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(userFile))) {
                    userID = ois.readInt();
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading user data: " + e.getMessage());
        }
    }

    private static void saveUserData(String email, int userID) {
        String userFile = getUserFile(email);
        try {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(userFile))) {
                oos.writeInt(userID);
            }
        } catch (Exception e) {
            System.err.println("Error saving user data: " + e.getMessage());
        }
    }

    private static boolean checkUserRegistered() {
        if (userID == -1 || currentEmail == null) {
            System.out.println("Please register first using: java Client register <email>");
            return false;
        }
        return true;
    }

    public static void registerUser(String[] args) {
        try {
            currentEmail = args[1];
            loadUserData(currentEmail);

            int previousID = userID;
            userID = server.register(currentEmail);
            saveUserData(currentEmail, userID);

            if (previousID != -1) {
                System.out.println("Welcome, " + currentEmail + " (ID: " + userID + ")");
            } else {
                System.out.println("Registration successful - UserID: " + userID);
            }
            printUsage();
        } catch (Exception e) {
            System.err.println("Registration failed: " + e.getMessage());
        }
    }

    public static void listAuctions() {
        if (!checkUserRegistered())
            return;
        try {
            AuctionItem[] items = server.listItems();
            System.out.println("\nLogged in as: " + currentEmail + " (ID: " + userID + ")");
            System.out.println("Current primary replica: " + server.getPrimaryReplicaID());

            if (items.length == 0) {
                System.out.println("No auctions currently available.");
                return;
            }

            for (AuctionItem item : items) {
                System.out.println("Item: " + item.itemID + ", " + item.name + ", "
                        + item.description + ", Current bid: " + item.highestBid);
            }
        } catch (Exception e) {
            System.err.println("List failed: " + e.getMessage());
        }
    }

    public static void createAuction(String[] args) {
        if (!checkUserRegistered())
            return;

        try {
            AuctionSaleItem newItem = new AuctionSaleItem();
            newItem.name = args[1];
            newItem.description = args[2];
            newItem.reservePrice = Integer.parseInt(args[3]);

            int newItemID = server.newAuction(userID, newItem);
            System.out.println("Auction created with ID: " + newItemID);
            System.out.println("Created by: " + currentEmail);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format in arguments");
        } catch (Exception e) {
            System.err.println("Create auction failed: " + e.getMessage());
        }
    } // issue here

    public static void placeBid(String[] args) {
        if (!checkUserRegistered())
            return;
        try {
            int itemId = Integer.parseInt(args[1]); // First argument after "bid"
            int price = Integer.parseInt(args[2]); // Second argument after "bid"

            boolean success = server.bid(userID, itemId, price);
            System.out.println(success ? "Bid successful" : "Bid failed");
            System.out.println("Bid placed by: " + currentEmail);
        } catch (Exception e) {
            System.err.println("Bid failed: " + e.getMessage());
        }
    }

    public static void closeAuction(String[] args) {
        if (!checkUserRegistered())
            return;
        try {
            int itemID = Integer.parseInt(args[2]);
            AuctionResult result = server.closeAuction(userID, itemID);

            System.out.println("Closing auction as: " + currentEmail);
            if (result != null) {
                if (result.winningEmail != null) {
                    System.out.println("Auction closed. Winner: " + result.winningEmail +
                            ", Price: " + result.winningPrice);
                } else {
                    System.out.println("Auction closed with no winner");
                }
            }
        } catch (RemoteException e) {
            if (e.getMessage().contains("Only the auction creator")) {
                System.out.println("Error: Only the auction creator can close this auction");
            } else {
                System.err.println("Close auction failed: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Close auction failed: " + e.getMessage());
        }
    }

    public static void getSpec(String[] args) {
        if (!checkUserRegistered())
            return;
        try {
            int itemID = Integer.parseInt(args[0]);
            AuctionItem auctionItem = server.getSpec(itemID);
            if (auctionItem != null) {
                System.out.println("Viewing as: " + currentEmail);
                System.out.println("Item: " + auctionItem.itemID + ", " +
                        auctionItem.name + ", " +
                        auctionItem.description + ", " +
                        "Current bid: " + auctionItem.highestBid);
            } else {
                System.out.println("No auction item found for ID: " + itemID);
            }
        } catch (Exception e) {
            System.err.println("GetSpec failed: " + e.getMessage());
        }
    }

    private static void printUsage() {
        System.out.println("\nCommands:");
        System.out.println("register <email>");
        System.out.println("list");
        System.out.println("create <name> <description> <reservePrice>");
        System.out.println("bid <itemID> <price>");
        System.out.println("close <userID> <itemID>");
    }

    private static void registerEmail() {
        System.out.print("Enter your email: ");
        try (Scanner scanner = new Scanner(System.in)) {
            currentEmail = scanner.nextLine();
        }
        loadUserData(currentEmail); // Load user data after getting email
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            return;
        }

        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            server = (Auction) registry.lookup("FrontEnd");

            String command = args[0];
            if (command.equals("register")) {
                if (args.length < 2) {
                    System.out.println("Usage: java Client register <email>");
                    return;
                }
                registerUser(args);
            } else {
                // Get email first before any operation
                registerEmail();

                if (userID == -1) {
                    System.out.println(
                            "User not registered. Please register first using: java Client register " + currentEmail);
                    return;
                }

                switch (command) {
                    case "list":
                        listAuctions();
                        break;
                    case "create":
                        if (args.length < 4 || args.length > 4) {
                            System.out.println("Usage: java Client create <name> <description> <reservePrice>");
                            return;
                        }
                        createAuction(args);
                        break;
                    case "bid":
                        if (args.length < 3 || args.length > 3) { // Changed from 4 to 3
                            System.out.println("Usage: java Client bid <itemID> <price>");
                            return;
                        }
                        placeBid(args);
                        break;
                    case "close":
                        if (args.length < 3 || args.length > 3) {
                            System.out.println("Usage: java Client close <userID> <itemID>");
                            return;
                        }
                        closeAuction(args);
                        break;
                    default:
                        if (args.length == 1 && command.matches("\\d+")) {
                            getSpec(args);
                        } else {
                            printUsage();
                        }
                }
            }
        } catch (Exception e) {
            System.err.println("Client Exception:");
            e.printStackTrace();
        }
    }
}
