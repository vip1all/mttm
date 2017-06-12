package com.staniul.query;

import com.staniul.xmlconfig.ConfigFile;
import com.staniul.util.StringUtil;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;
import org.apache.commons.configuration2.Configuration;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;

@ConfigFile("query.xml")
public class Query {
    /**
     * Apache Log4j logger to log errors and activities.
     */
    private static Logger log = Logger.getLogger(Query.class);

    /**
     * Backed jts3serverquery object for communication with teamspeak 3 server query.
     */
    private JTS3ServerQuery jts3ServerQuery;

    /**
     * Configuration of this class. Contains information about teamspeak 3 connection.
     */
    private Configuration configuration;

    /**
     * Thread that is keeping the connection up with teamspeak 3 server. Prevents server from timeouting application
     * after not sending message after few minutes, by sending every 2 minutes a dummy message.
     */
    private Thread connectionKeeper;

    /**
     * Indicates if method connect was already called or no, and if query should be connected with teamspeak 3 server
     * or not. Used in connection keeper to determine if it should try reconnecting.
     */
    private boolean connected;

    /**
     * Used to create a new query object. Should be only created by spring, since we only use one connection with
     * teamspeak 3 server.
     *
     * @param configuration A {@link Configuration} containing connection information for teamspeak 3 server.
     */
    public Query(Configuration configuration) {
        this.configuration = configuration;
        this.jts3ServerQuery = new JTS3ServerQuery("Query");
        connected = false;
    }

    /**
     * Connects to teamspeak 3 server.
     *
     * @throws Exception If jts3serverquery fails to establish connection with teamspeak 3 server.
     */
    public void connect() throws Exception {
        internalConnect();
        connectionKeeper = new Thread(new ConnectionKeeper(), "Query Connection Keeper");
        connectionKeeper.start();
        this.connected = true;
    }

    /**
     * Connects to teamspeak 3 server with jts3serverquery.
     *
     * @throws Exception If jts3serverquery fails to establish connection with teamspeak 3 server.
     */
    private void internalConnect() throws Exception {
        jts3ServerQuery.connectTS3Query(configuration.getString("ip"), configuration.getInt("port"));
        jts3ServerQuery.loginTS3(configuration.getString("login"), configuration.getString("password"));
        jts3ServerQuery.selectVirtualServer(configuration.getInt("serverid"));
    }

    /**
     * Disconnects from teamspeak 3 server.
     * Kills connection keeper.
     */
    public void disconnect() {
        connected = false;
        jts3ServerQuery.closeTS3Connection();
        connectionKeeper.interrupt();
    }

    /**
     * Gets client information from teamspeak 3 server about currently connected client with given id.
     *
     * @return {@code Client} object containing information about client with given id.
     *
     * @throws QueryException If server query request returns with error, client does not exists or probably when
     *                        query've been disconnected from teamspeak 3 server.
     */
    public Client getClientInfo(int clientId) throws QueryException {
        try {
            HashMap<String, String> clientInfo = jts3ServerQuery.getInfo(JTS3ServerQuery.INFOMODE_CLIENTINFO, clientId);
            return new Client(clientId, clientInfo);
        } catch (TS3ServerQueryException e) {
            throwQueryException("Failed to get client information from teamspeak 3 server.", e);
            return null;
        }
    }

    /**
     * Gets list of clients currently connected to teamspeak 3 server.
     * Clients are stored in ArrayList.
     *
     * @return {@code ArrayList<Client>} containing all clients currently connected to teamspeak 3 server.
     *
     * @throws QueryException If server query request returns with error, probably when query've been disconnected from
     *                        teamspeak 3 server.
     */
    public ArrayList<Client> getClientList() throws QueryException {
        try {
            Vector<HashMap<String, String>> clientList = jts3ServerQuery.getList(JTS3ServerQuery.LISTMODE_CLIENTLIST, "-uid,-away,-voice,-times,-groups,-info,-icon,-country,-ip");
            ArrayList<Client> result = new ArrayList<>(clientList.size());
            clientList.stream()
                    .map(c -> new Client(Integer.parseInt(c.get("clid")), c))
                    .forEach(result::add);
            return result;
        } catch (TS3ServerQueryException ex) {
            throwQueryException("Failed to get client list from teamspeak 3 server", ex);
            return null;
        }
    }

    /**
     * Gets client information from teamspeak 3 database. These are all offline information stored about client.
     * Too see what is stored see {@link ClientDatabase}.
     * If you want list of currently connected clients see {@link #getClientInfo(int)}.
     *
     * @param clientDatabaseId Database id of client.
     *
     * @return {@link ClientDatabase} object containing all database information about client.
     *
     * @throws QueryException If query fails to get information from teamspeak 3 server, client with given id does not
     *                        exists or there was a problem with connection with teamspeak 3 server.
     */
    public ClientDatabase getClientDatabaseInfo(int clientDatabaseId) throws QueryException {
        try {
            HashMap<String, String> clientInfo = jts3ServerQuery.getInfo(JTS3ServerQuery.INFOMODE_CLIENTDBINFO, clientDatabaseId);
            return new ClientDatabase(clientDatabaseId, clientInfo);
        } catch (TS3ServerQueryException e) {
            throwQueryException("Failed to get client database information from teamspeak 3 server.", e);
            return null;
        }
    }

    /**
     * Gets channel information from teamspeak 3 server with given id.
     *
     * @param channelId Channel id.
     *
     * @return {@code Channel} object containing information about channel.
     *
     * @throws QueryException When query fails to get information from teamspeak 3 server, that could be channel does
     *                        not exists with given id or connection with teamspeak 3 server was interrupted.
     */
    public Channel getChannelInfo(int channelId) throws QueryException {
        try {
            HashMap<String, String> channelInfo = jts3ServerQuery.getInfo(JTS3ServerQuery.INFOMODE_CHANNELINFO, channelId);
            return new Channel(channelId, channelInfo);
        } catch (TS3ServerQueryException e) {
            throwQueryException("Failed to get channel information from teamspeak 3 server.", e);
            return null;
        }
    }

    /**
     * Gets channel list from teamspeak 3 server.
     *
     * @return {@code ArrayList<Channel>} containing information about channels currently present on teamspeak 3 server.
     *
     * @throws QueryException When query fails to get channel list from teamspeak 3 server, because connection with
     *                        teamspeak 3 being interrupted.
     */
    public ArrayList<Channel> getChannelList() throws QueryException {
        try {
            Vector<HashMap<String, String>> channelList = jts3ServerQuery.getList(JTS3ServerQuery.LISTMODE_CHANNELLIST, "-topic,-flags,-voice,-limits,-icon,-secondsempty");
            ArrayList<Channel> result = new ArrayList<>(channelList.size());
            channelList.stream().map(c -> new Channel(Integer.parseInt(c.get("cid")), c)).forEach(result::add);
            return result;
        } catch (TS3ServerQueryException e) {
            throwQueryException("Failed to get channel list from teamspeak 3 server.", e);
            return null;
        }
    }

    /**
     * Sets clients channel group for specified channel.
     *
     * @param clientDatabaseId Clients database id.
     * @param channelId        Channel id.
     * @param groupId          Channel group id.
     *
     * @throws QueryException When query fails to assign group for teamspeak 3 client.
     */
    public void setChannelGroup(int clientDatabaseId, int channelId, int groupId) throws QueryException {
        String template = "setclientchannelgroup cldbid=%d cid=%d cgid=%d";
        Map<String, String> serverResponse = jts3ServerQuery.doCommand(String.format(template, clientDatabaseId, channelId, groupId));
        checkAndThrowQueryException("Failed to set channel group for client!", serverResponse);
    }

    /**
     * Kicks client from server with reason {@code RULES VIOLATION}.
     *
     * @param clientId Client id.
     *
     * @throws QueryException When query fails to kick client from server, might be he disconnected before or connection
     *                        with teamspeak 3 server was interrupted.
     */
    public void kickClient(int clientId) throws QueryException {
        kickClient(clientId, "RULES VIOLATION");
    }

    /**
     * Kicks client from server with specified reason.
     *
     * @param clientId Client id.
     * @param msg      Reason for kick, will be displayed to client in kick message dialog.
     *
     * @throws QueryException When query fails to kick client from server, might be he disconnected before or connection
     *                        with teamspeak 3 server was interrupted.
     */
    public void kickClient(int clientId, String msg) throws QueryException {
        try {
            jts3ServerQuery.kickClient(clientId, false, msg);
        } catch (TS3ServerQueryException e) {
            throwQueryException("Failed to kick client from server.", e);
        }
    }

    /**
     * Kicks client from channel with specified message.
     *
     * @param clientId Client id.
     * @param msg      Reason for kick, will be displayed to client in kick message dialog.
     *
     * @throws QueryException When query fails to kick client from server, might be he disconnected before or connection
     *                        with teamspeak 3 server was interrupted.
     */
    public void kickClientFromChannel(int clientId, String msg) throws QueryException {
        try {
            jts3ServerQuery.kickClient(clientId, true, msg);
        } catch (TS3ServerQueryException e) {
            throwQueryException("Failed to kick client from channel.", e);
        }
    }

    /**
     * <p>Sends message to client. If message is too long it will divide it on spaces. If you want the message to be
     * divided otherwise then use {@link #sendTextMessageToClient(int, String[])} or {@link
     * #sendTextMessageToClient(int, Collection)}<br /></p>
     * <p>
     * <p>Teamspeak 3 max message length is 1024 byte long in UTF-8 encoding. For sake of simplicity messages are
     * divided after 512 chars on space. Since most characters uses 1 byte in UTF-8, in most case use it should be
     * sufficient.<br /> Otherwise you need to divide message by yourself or exception will be thrown.</p>
     *
     * @param clientId Id of client to whom we send message.
     * @param message  Message to send.
     *
     * @throws QueryException When teamspeak 3 query fails to send message.
     */
    public void sendTextMessageToClient(int clientId, String message) throws QueryException {
        sendTextMessageToClient(clientId, StringUtil.splitOnSize(message, " ", 512));
    }

    /**
     * Sends messages to client. Messages cannot be longer then 1024 bytes long.
     *
     * @param clientId Id of client to whom we send message.
     * @param messages Collection of Strings. Each string will be sent as separate message. Each message cannot be
     *                 longer then 1024 byte.
     *
     * @throws QueryException When messages are too long or teamspeak 3 query fails to send messages.
     */
    public void sendTextMessageToClient(int clientId, Collection<String> messages) throws QueryException {
        sendTextMessageToClient(clientId, (String[]) messages.toArray());
    }

    /**
     * Sends messages to client. Messages cannot be longer then 1024 bytes long.
     *
     * @param clientId Id of client to whom we send message.
     * @param messages Array of Strings containing messages to send. Each message must be max of 1024 bytes long.
     *
     * @throws QueryException When messages are too long or teamspeak 3 query fails to send the message.
     */
    public void sendTextMessageToClient(int clientId, String[] messages) throws QueryException {
        for (int i = 0; i < messages.length; i++) {
            String msg = messages[i];
            if (msg.getBytes().length > 1024)
                throw new QueryException("Messages are too long! Message too long index: " + i);
        }

        for (String msg : messages) {
            try {
                jts3ServerQuery.sendTextMessage(clientId, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, msg);
            } catch (TS3ServerQueryException e) {
                throwQueryException("Failed to send message to client!", e);
            }
        }
    }

    /**
     * <p>Sends message to channel. If message is too long it will divide it on spaces. If you want the message to be
     * divided otherwise then use {@link #sendTextMessageToChannel(int, String[])} or {@link
     * #sendTextMessageToChannel(int, Collection)}<br /></p>
     * <p>
     * <p>Teamspeak 3 max message length is 1024 byte long in UTF-8 encoding. For sake of simplicity messages are
     * divided after 512 chars on space. Since most characters uses 1 byte in UTF-8, in most case use it should be
     * sufficient.<br /> Otherwise you need to divide message by yourself or exception will be thrown.</p>
     *
     * @param channelId Id of channel.
     * @param message   Message to send.
     *
     * @throws QueryException WHen teamspeak 3 server query fails to send message.
     */
    public void sendTextMessageToChannel(int channelId, String message) throws QueryException {
        sendTextMessageToChannel(channelId, StringUtil.splitOnSize(message, " ", 512));
    }

    /**
     * Sends messages to channel. Max message length is 1024 byte in UTF-8 encoding.
     *
     * @param channelId Id of channel.
     * @param messages  Messages to send.
     *
     * @throws QueryException When messages are too long or teamspeak 3 query fails to send messages.
     */
    public void sendTextMessageToChannel(int channelId, Collection<String> messages) throws QueryException {
        sendTextMessageToChannel(channelId, messages.toArray(new String[messages.size()]));
    }

    /**
     * Sends messages to channel. Max message length is 1024 byte in UTF-8 encoding.
     *
     * @param channelId Id of channel.
     * @param messages  Messages to send.
     *
     * @throws QueryException When messages are too long or teamspeak 3 query fails to send messages.
     */
    public void sendTextMessageToChannel(int channelId, String[] messages) throws QueryException {
        for (int i = 0; i < messages.length; i++) {
            String msg = messages[i];
            if (msg.getBytes().length > 1024)
                throw new QueryException("Messages are too long! Message too long index: " + i);
        }

        for (String msg : messages) {
            try {
                jts3ServerQuery.sendTextMessage(channelId, JTS3ServerQuery.TEXTMESSAGE_TARGET_CHANNEL, msg);
            } catch (TS3ServerQueryException e) {
                throwQueryException("Failed to send message to client!", e);
            }
        }
    }

    /**
     * Throws {@link QueryException} based on {@link TS3ServerQueryException}. Used multiple times so its a helpful
     * method. You can specify message that will be added to exception.
     *
     * @param msg Additional message added to exception.
     * @param ex  {@code TS3ServerQueryException} on which {@code QueryException} should be based.
     *
     * @throws QueryException Created {@code QueryException} based on given {@code TS3ServerQueryException}
     */
    private void throwQueryException(String msg, TS3ServerQueryException ex) throws QueryException {
        throw new QueryException(msg, ex, ex.getErrorID(), ex.getErrorMessage());
    }

    /**
     * For information see {@link #throwQueryException(String, TS3ServerQueryException)}
     *
     * @param ex
     *
     * @throws QueryException
     * @see #throwQueryException(String, TS3ServerQueryException)
     */
    private void throwQueryException(TS3ServerQueryException ex) throws QueryException {
        throw new QueryException(ex, ex.getErrorID(), ex.getErrorMessage());
    }

    /**
     * Checks if response returned with error and if so throws exception, otherwise returns silently.
     *
     * @param serverResponse Response from teamspeak 3 server after invoking command.
     *
     * @throws QueryException When teamspeak 3 server query returned with error it throws exception.
     */
    private void checkAndThrowQueryException(Map<String, String> serverResponse) throws QueryException {
        if ("ok".equals(serverResponse.get("msg")) && "0".equals(serverResponse.get("id")))
            return;

        throw new QueryException(Integer.parseInt(serverResponse.get("id")), serverResponse.get("msg"));
    }

    /**
     * Same as {@link #checkAndThrowQueryException(Map)}, but lets you specify message in exception.
     *
     * @param msg            Message to be added to exception.
     * @param serverResponse Response from teamspeak 3 server after invoking command.
     *
     * @throws QueryException When teamspeak 3 server query returned with error it throws exception.
     */
    private void checkAndThrowQueryException(String msg, Map<String, String> serverResponse) throws QueryException {
        if ("ok".equals(serverResponse.get("msg")) && "0".equals(serverResponse.get("id")))
            return;

        throw new QueryException(msg, Integer.parseInt(serverResponse.get("id")), serverResponse.get("msg"));
    }

    /**
     * Every 2 minutes sends dummy message to keep up the connection with teamspeak 3 server preventing teamspeak 3
     * server query from not sending notification messages in case of no action performed on teamspeak 3 server.
     * Started with every connection with teamspeak 3 server. If teamspeak 3 drops connection with query, tries to
     * reconnect once.
     */
    private class ConnectionKeeper implements Runnable {
        @Override
        public void run() {
            try {
                while (connected) {
                    if (!jts3ServerQuery.isConnected()) internalConnect();
                    jts3ServerQuery.doCommand("Keeping connection up!");
                    Thread.sleep(TimeUnit.MINUTES.toMillis(2));
                }
            } catch (InterruptedException e) {
                log.info("Connection Keeper in Query stopped.");
            } catch (Exception e) {
                log.error("Connection Keeper in Query stopped because of exception!", e);
            }
        }
    }
}
