// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.walmart.welcomeuser;

import com.codepoetics.protonpack.collectors.CompletableFutures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.bot.builder.*;
import com.microsoft.bot.schema.*;
import jdk.nashorn.internal.ir.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.json.simple.parser.JSONParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.concurrent.CompletionException;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;


/**
 * This class implements the functionality of the Bot.
 *
 * Represents a bot that processes incoming activities.
 * For each user interaction, an instance of this class is created and the onTurn method is called.
 * This is a Transient lifetime service. Transient lifetime services are created
 * each time they're requested. For each Activity received, a new instance of this
 * class is created. Objects that are expensive to construct, or have a lifetime
 * beyond the single turn, should be carefully managed.
 * For example, the "MemoryStorage" object and associated
 * StatePropertyAccessor{T} object are created with a singleton lifetime.
 * 
 * <p>
 * This is where application specific logic for interacting with the users would
 * be added. This class tracks the conversation state through a POJO saved in
 * {@link UserState} and demonstrates welcome messages and state.
 * </p>
 *
 * @see WelcomeUserState
 */
public class WelcomeUserBot extends ActivityHandler {
    // Messages sent to the user.
    private static final String WELCOME_MESSAGE =
        "This is a simple Welcome Bot sample.";

    private static final String FIRST_WELCOME_ONE =
        "You are seeing this message because this was your first message ever to this bot.";

    private static final String FIRST_WELCOME_TWO =
        "It is a good practice to welcome the user and provide personal greeting. For example: Welcome %s.";

    private final UserState userState;

    // Initializes a new instance of the "WelcomeUserBot" class.
    @Autowired
    public WelcomeUserBot(UserState withUserState) {
        userState = withUserState;
    }

    /**
     * Normal onTurn processing, with saving of state after each turn.
     *
     * @param turnContext The context object for this turn. Provides information
     *                    about the incoming activity, and other data needed to
     *                    process the activity.
     * @return A future task.
     */
    @Override
    public CompletableFuture<Void> onTurn(TurnContext turnContext) {
        return super.onTurn(turnContext)
            .thenCompose(saveResult -> userState.saveChanges(turnContext));
    }

    /**
     * Greet when users are added to the conversation.
     *
     * <p>Note that all channels do not send the conversation update activity.
     * If you find that this bot works in the emulator, but does not in
     * another channel the reason is most likely that the channel does not
     * send this activity.</p>
     *
     * @param membersAdded A list of all the members added to the conversation, as
     *                     described by the conversation update activity.
     * @param turnContext  The context object for this turn.
     * @return A future task.
     */
    @Override
    protected CompletableFuture<Void> onMembersAdded(
        List<ChannelAccount> membersAdded,
        TurnContext turnContext
    ) {
        return membersAdded.stream()
            .filter(
                member -> !StringUtils
                    .equals(member.getId(), turnContext.getActivity().getRecipient().getId())
            )
            .map(
                channel -> turnContext
                    .sendActivities(
                        MessageFactory.text(
                            "Hi there - " + channel.getName() + ". " + WELCOME_MESSAGE
                        )
                    )
            )
            .collect(CompletableFutures.toFutureList())
            .thenApply(resourceResponses -> null);
    }

    /**
     * This will prompt for a user name, after which it will send info about the
     * conversation. After sending information, the cycle restarts.
     *
     * @param turnContext The context object for this turn.
     * @return A future task.
     */
    @Override
    protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {
        // Get state data from UserState.
        StatePropertyAccessor<WelcomeUserState> stateAccessor =
            userState.createProperty("WelcomeUserState");
        CompletableFuture<WelcomeUserState> stateFuture =
            stateAccessor.get(turnContext, WelcomeUserState::new);

        return stateFuture.thenApply(thisUserState -> {
            if (!thisUserState.getDidBotWelcomeUser()) {
                thisUserState.setDidBotWelcomeUser(true);

                // the channel should send the user name in the 'from' object
                String userName = turnContext.getActivity().getFrom().getName();
                return turnContext
                    .sendActivities(
                        MessageFactory.text(FIRST_WELCOME_ONE),
                        MessageFactory.text(String.format(FIRST_WELCOME_TWO, userName))
                    );
            } else {
                // This example hardcodes specific utterances. 
                // You should use LUIS or QnA for more advance language understanding.
                String text = turnContext.getActivity().getText().toLowerCase();
                switch (text) {
                    case "hello":
                    case "hi":
                        return turnContext.sendActivities(MessageFactory.text("You said " + text));

                    case "intro":
                    case "help":
                        return sendIntroCard(turnContext);


                    default:
                        return sender(turnContext);
                }
            }
        })
            // Save any state changes. 
            .thenApply(response -> userState.saveChanges(turnContext))                
            // make the return value happy.
            .thenApply(task -> null);
    }

    private CompletableFuture<ResourceResponse> sendIntroCard(TurnContext turnContext) {
        HeroCard card = new HeroCard();
        card.setTitle("Welcome to Bot Framework!");
        card.setText(
            "Welcome to Welcome Users bot sample! This Introduction card "
                + "is a great way to introduce your Bot to the user and suggest "
                + "some things to get them started. We use this opportunity to "
                + "recommend a few next steps for learning more creating and deploying bots."
        );

        CardImage image = new CardImage();
        image.setUrl("https://aka.ms/bf-welcome-card-image");

        card.setImages(Collections.singletonList(image));

        CardAction overviewAction = new CardAction();
        overviewAction.setType(ActionTypes.OPEN_URL);
        overviewAction.setTitle("Get an overview");
        overviewAction.setText("Get an overview");
        overviewAction.setDisplayText("Get an overview");
        overviewAction.setValue(
            "https://docs.microsoft.com/en-us/azure/bot-service/?view=azure-bot-service-4.0"
        );

        CardAction questionAction = new CardAction();
        questionAction.setType(ActionTypes.OPEN_URL);
        questionAction.setTitle("Ask a question");
        questionAction.setText("Ask a question");
        questionAction.setDisplayText("Ask a question");
        questionAction.setValue("https://stackoverflow.com/questions/tagged/botframework");

        CardAction deployAction = new CardAction();
        deployAction.setType(ActionTypes.OPEN_URL);
        deployAction.setTitle("Learn how to deploy");
        deployAction.setText("Learn how to deploy");
        deployAction.setDisplayText("Learn how to deploy");
        deployAction.setValue(
            "https://docs.microsoft.com/en-us/azure/bot-service/bot-builder-howto-deploy-azure?view=azure-bot-service-4.0"
        );
        card.setButtons(Arrays.asList(overviewAction, questionAction, deployAction));

        Activity response = MessageFactory.attachment(card.toAttachment());
        return turnContext.sendActivity(response);
    }

    private CompletableFuture<ResourceResponse> sender(TurnContext turnContext)
    {
        String message = turnContext.getActivity().getText();

        JSONParser parser = new JSONParser();

        Object obj = null;
        try {
            obj = parser.parse(new FileReader("/Users/y0g037u/Desktop/teamsbot/src/main/java/com/walmart/welcomeuser/orderdetail.json"));
        } catch (ParseException | FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        JSONObject jsonObject = (JSONObject) obj;
        String id = (String) jsonObject.get("frId");

        if(message.equals(id)) {
            List<Attachment> attachments = new ArrayList<>();
            Activity reply = MessageFactory.attachment(attachments);
            reply.getAttachments().add(createAdaptiveCardAttachment(id));
            return turnContext.sendActivity(reply);

        }
        else{
            return turnContext.sendActivity("Invalid Id");
        }
a
    }

    private static Attachment createAdaptiveCardAttachment(String id) {

        JSONParser parser = new JSONParser();
        Object obj = null;
        try {
            obj = parser.parse(new FileReader("/Users/y0g037u/Desktop/teamsbot/src/main/resources/adaptiveCard.json"));
        } catch (ParseException | FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        JSONObject jsonObject = (JSONObject) obj;

        ArrayList body = (ArrayList) jsonObject.get("body");
        JSONObject body1= (JSONObject)body.get(0);
        body1.put("text","US|5722106988174");
       System.out.println("After ID value updated : "+jsonObject);


        try {
            Attachment attachment = new Attachment();
            attachment.setContentType("application/vnd.microsoft.card.adaptive");
            attachment.setContent(Serialization.jsonToTree(jsonObject.toJSONString()));

            return attachment;

        } catch (IOException e) {
            e.printStackTrace();
            return new Attachment();
        }}
}
