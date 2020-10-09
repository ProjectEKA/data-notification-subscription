package in.projecteka.datanotificationsubscription.common;

import lombok.Builder;
import lombok.Value;

import java.util.Optional;

@Builder
@Value
public class Caller {
    String username;
    boolean isServiceAccount;
    String sessionId;
    boolean isHealthNumber;
    String role;
    boolean verified;

    public Caller(String username, boolean isServiceAccount,String role,boolean verified) {
        this.username = username;
        this.isServiceAccount = isServiceAccount;
        this.role = role;
        this.sessionId = null;
        this.isHealthNumber = false;
        this.verified = verified;
    }

    public Caller(String username, boolean isServiceAccount) {
        this.username = username;
        this.isServiceAccount = isServiceAccount;
        this.role = null;
        this.sessionId = null;
        this.isHealthNumber = false;
        this.verified = false;
    }

    public Caller(String username, boolean isServiceAccount, String sessionId) {
        this.username = username;
        this.isServiceAccount = isServiceAccount;
        this.sessionId = sessionId;
        this.role = null;
        this.isHealthNumber = false;
        this.verified = false;
    }

    public static Caller withHealthNumber(String username) {
        return new Caller(username, false, null, true, null);
    }

    public Caller(String username, boolean isServiceAccount, String sessionId, boolean isHealthNumber, String role) {
        this.username = username;
        this.isServiceAccount = isServiceAccount;
        this.sessionId = sessionId;
        this.isHealthNumber = isHealthNumber;
        this.role = role;
        this.verified = false;
    }

    public Caller(String username, boolean isServiceAccount, String sessionId, boolean isHealthNumber, String role, boolean verified) {
        this.username = username;
        this.isServiceAccount = isServiceAccount;
        this.sessionId = sessionId;
        this.isHealthNumber = isHealthNumber;
        this.role = role;
        this.verified = verified;
    }


    public Optional<String> getRole() {
        return Optional.ofNullable(role);
    }
}
