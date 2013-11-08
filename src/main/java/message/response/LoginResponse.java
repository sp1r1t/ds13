package message.response;

import message.Response;

import java.util.UUID;

/**
 * Authenticates the client with the provided username and password.
 * <p/>
 * <b>Request</b>:<br/>
 * {@code !login &lt;username&gt; &lt;password&gt;}<br/>
 * <b>Response:</b><br/>
 * {@code !login success}<br/>
 * or<br/>
 * {@code !login wrong_credentials}
 *
 * @see message.request.LoginRequest
 */
public class LoginResponse implements Response {
    private static final long serialVersionUID = 3134831924072300109L;

    public enum Type {
        SUCCESS("Successfully logged in."),
            WRONG_CREDENTIALS("Wrong username or password."),
            IS_LOGGED_IN("User is already logged in");

        String message;

        Type(String message) {
            this.message = message;
        }
    }

    private final Type type;
    private final UUID sid;

    public LoginResponse(Type type, UUID sid) {
        this.type = type;
        this.sid = sid;
    }

    public LoginResponse(Type type) {
        this.type = type;
        this.sid = null;
    }

    public Type getType() {
        return type;
    }

    public UUID getSid() {
        return sid;
    }

    @Override
    public String toString() {
        return "!login " + getType().name().toLowerCase();
    }
}
