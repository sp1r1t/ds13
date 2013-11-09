package message.request;

import message.Request;

import java.util.UUID;

/**
 * Performs a logout if necessary and closes open connections between client and proxy.
 * <p/>
 * <b>Request</b>:<br/>
 * {@code !logout}<br/>
 * <b>Response:</b><br/>
 * {@code !logout &lt;message&gt;}<br/>
 *
 * @see message.response.MessageResponse
 */
public class LogoutRequest implements Request {
    private static final long serialVersionUID = -1496068214330800650L;
    
    private final UUID sid;

    public LogoutRequest(UUID sid) {
        this.sid = sid;
    }

    public UUID getSid() {
        return sid;
    }
    
    @Override
    public String toString() {
        return "!logout";
    }
}
