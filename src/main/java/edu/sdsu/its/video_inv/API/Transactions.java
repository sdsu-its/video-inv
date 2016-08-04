package edu.sdsu.its.video_inv.API;

import com.google.gson.Gson;
import edu.sdsu.its.video_inv.DB;
import edu.sdsu.its.video_inv.Models.Transaction;
import edu.sdsu.its.video_inv.Models.User;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.log4j.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Manage Transaction Endpoints (Get and Create).
 * Once created, a transaction is non-updatable to ensure the chain-of-custody is uninterrupted.
 *
 * @author Tom Paulus
 *         Created on 7/29/16.
 */
@Path("transaction")
public class Transactions {
    private static final int TRANSACTION_ID_LENGTH = 6;
    private static final Logger LOGGER = Logger.getLogger(Transactions.class);

    private static String generateTransactionID() {
        String s = RandomStringUtils.random(TRANSACTION_ID_LENGTH);
        LOGGER.debug("GGenerated Transaction ID: " + s);
        return s;
    }

    /**
     * List all or a specific transaction.
     * If no transactionID is supplied, all transactions will be returned.
     *
     * @param sessionToken  {@link String} User Session Token
     * @param transactionID {@link String} Transaction ID to fetch, All transactions will be returned if Null
     * @return {@link Response} JSON Transaction Array {@see Models.Transaction}
     */
    @GET
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTransaction(@HeaderParam("session") final String sessionToken,
                                   @QueryParam("id") String transactionID) {

        User user = Session.validate(sessionToken);
        Gson gson = new Gson();
        if (user == null || user.pubID != 0) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson(new SimpleMessage("Error", "Invalid Session Token"))).build();
        }
        if (transactionID == null) transactionID = "";
        LOGGER.info("Recieved Request for Transaction Where ID=" + transactionID);

        String restriction = "";
        if (transactionID.length() > 0) {
            restriction = "t.id = " + transactionID;
        }

        Transaction[] transactions = DB.getTransaction(restriction);
        if (transactionID.length() > 0 && transactions.length == 0)
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson(new SimpleMessage("Error", "No transaction with that ID was found"))).build();

        return Response.status(Response.Status.OK).entity(gson.toJson(transactionID)).build();
    }

    /**
     * Create a New Transaction.
     *
     * @param sessionToken {@link String} User Session Token
     * @param payload      {@link String} JSON Transaction Object
     * @return {@link Response} Status Message
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createTransaction(@HeaderParam("session") final String sessionToken,
                                      final String payload) {
        User user = Session.validate(sessionToken);
        Gson gson = new Gson();
        if (user == null || user.pubID != 0) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson(new SimpleMessage("Error", "Invalid Session Token"))).build();
        }
        if (payload == null || payload.length() == 0)
            return Response.status(Response.Status.PRECONDITION_FAILED).entity(gson.toJson(new SimpleMessage("Error", "Empty Request Payload"))).build();
        LOGGER.info("Recieved Request to Create Transaction");
        LOGGER.debug("Transaction Payload: " + payload);

        Transaction createTransaction = gson.fromJson(payload, Transaction.class);
        createTransaction.id = generateTransactionID();

        if (DB.getUser("id = " + createTransaction.owner.dbID).length == 0)
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson(new SimpleMessage("Error", "Owner is not a valid user"))).build();

        User[] supervisor = DB.getUser("id = " + createTransaction.supervisor.dbID);
        if (supervisor.length == 0 || !supervisor[0].supervisor)
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson(new SimpleMessage("Error", "Supervisor is not a valid user, or does not have the authority to authorize new transactions"))).build();

        for (Transaction.Component component : createTransaction.components) {
            if (DB.getItem("i.id = " + component.id).length == 0)
                return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson(new SimpleMessage("Error", String.format("Item with ID (%d/%d) is not a valid Item", component.id, component.pubID)))).build();
        }

        DB.createTransaction(createTransaction);

        return Response.status(Response.Status.CREATED).entity(gson.toJson(createTransaction)).build();
    }
}