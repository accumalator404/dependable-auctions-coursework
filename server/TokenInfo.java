public class TokenInfo implements java.io.Serializable {
    String token;      // one-time use token
    long expiryTime;   // Unix timestamp for expiration
}