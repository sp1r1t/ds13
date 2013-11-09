package message.request;

import message.Request;

import java.util.UUID;

/**
 * Buys additional credits for the authenticated user.
 * <p/>
 * <b>Request</b>:<br/>
 * {@code !buy &lt;credits&gt;}<br/>
 * <b>Response:</b><br/>
 * {@code !credits &lt;total_credits&gt;}<br/>
 * 
 * @see message.response.BuyResponse
 */
public class BuyRequest implements Request {
    private static final long serialVersionUID = 8589241767079930421L;

    private final long credits;

    private final UUID sid;

    public BuyRequest(UUID sid, long credits) {
        this.sid = sid;
        this.credits = credits;
    }

    public long getCredits() {
        return credits;
    }

    public UUID getSid() {
        return sid;
    }

    @Override
    public String toString() {
        return "!buy " + getCredits();
    }
}
