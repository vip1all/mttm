package com.staniul.teamspeak.commands;

import com.staniul.xmlconfig.UseConfig;
import com.staniul.teamspeak.query.Client;
import com.staniul.teamspeak.query.Query;
import com.staniul.teamspeak.query.QueryException;
import com.staniul.xmlconfig.WireConfig;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.Logger;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Command messages send command responses to clients after command were executed, whether it was successful or not.
 */
@Component
@Aspect
@UseConfig("cmdmsg.xml")
public class CommandMessengerAspect {
    private static Logger log = Logger.getLogger(CommandMessengerAspect.class);

    @WireConfig
    private XMLConfiguration config;
    private Query query;

    @Autowired
    public CommandMessengerAspect(Query query) throws ConfigurationException {
        this.query = query;
    }

    @Pointcut(value = "execution(com.staniul.teamspeak.commands.CommandResponse * (com.staniul.teamspeak.query.Client,..)) && " +
            "args(client,..)", argNames = "client")
    public void commandExecution (Client client) {

    }

    @AfterReturning(value = "commandExecution(client)", returning = "response", argNames = "client,response")
    public void sendMessageAfterCommandReturn (Client client, CommandResponse response) {
        if (response.getStatus() != CommandExecutionStatus.EXECUTED_SUCCESSFULLY) {
            response.setMessage(new String[]{ config.getString(response.getStatus().toString().toLowerCase()) });
        }

        try {
            query.sendTextMessageToClient(client.getId(), response.getMessage());
        } catch (QueryException e) {
            log.error("Failed to send message to client!", e);
        }
    }

    @AfterThrowing(value = "commandExecution(client)", argNames = "client")
    public void sendMessageAfterCommandThrow (Client client) {
        String message = config.getString(CommandExecutionStatus.EXECUTION_TERMINATED.toString().toLowerCase());
        try {
            query.sendTextMessageToClient(client.getId(), message);
        } catch (QueryException e) {
            log.error("Failed to send message to client!", e);
        }
    }
}
