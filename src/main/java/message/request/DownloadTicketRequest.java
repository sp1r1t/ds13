package message.request;

import message.Request;

import java.util.UUID;

/**
 * Requests a {@link model.DownloadTicket} in order to download a file from a file server.
 * <p/>
 * <b>Request (client to proxy)</b>:<br/>
 * {@code !download &lt;filename&gt;}<br/>
 * <b>Response (proxy to client):</b><br/>
 * {@code !download &lt;ticket&gt;}<br/>
 *
 * @see message.response.DownloadTicketResponse
 */
public class DownloadTicketRequest implements Request {
    private static final long serialVersionUID = 1183675324570817315L;

    private final String filename;

    private final UUID sid;

    public DownloadTicketRequest(UUID sid, String filename) {
        this.sid = sid;
        this.filename = filename;
    }

    public UUID getSid() {
        return sid;
    }
    public String getFilename() {
        return filename;
    }

    @Override
    public String toString() {
        return "!download " + getFilename();
    }
}
