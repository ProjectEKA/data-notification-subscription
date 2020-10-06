package in.projecteka.datanotificationsubscription.common;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Caller {
    String username;
    boolean isServiceAccount;
    String sessionId;
    boolean isHealthNumber;

    public Caller(String username, boolean isServiceAccount) {
        this.username = username;
        this.isServiceAccount = isServiceAccount;
        this.sessionId = null;
        this.isHealthNumber = false;
    }

    public Caller(String username, boolean isServiceAccount, String sessionId) {
        this.username = username;
        this.isServiceAccount = isServiceAccount;
        this.sessionId = sessionId;
        this.isHealthNumber = false;
    }

    public static Caller withHealthNumber(String username) {
        return new Caller(username, false, null, true);
    }

    public Caller(String username, boolean isServiceAccount, String sessionId, boolean isHealthNumber) {
        this.username = username;
        this.isServiceAccount = isServiceAccount;
        this.sessionId = sessionId;
        this.isHealthNumber = isHealthNumber;
    }
}
