package slaynash.lum.bot.discord;

import java.util.List;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class PrivateMessagesHandler {
    public static final String LOG_IDENTIFIER = "PrivateMessagesHandler";

    public static void handle(MessageReceivedEvent event) {

        if (event.getAuthor().getIdLong() != JDAManager.getJDA().getSelfUser().getIdLong()) {
            System.out.println(String.format("[DM] %s%s%s: %s",
                    event.getAuthor().getEffectiveName(),
                    event.getMessage().isEdited() ? " *edited*" : "",
                    event.getMessage().getType().isSystem() ? " *system*" : "",
                    event.getMessage().getContentRaw().replace("\n", "\n\t\t")));
            List<Attachment> attachments = event.getMessage().getAttachments();
            if (!attachments.isEmpty()) {
                System.out.println(attachments.size() + " Files");
                for (Attachment a : attachments)
                    System.out.println(" - " + a.getUrl());
            }
            if (ScamShield.checkForFishingPrivate(event)) {
                System.out.println("I was DM'd a Scam");
                return;
            }

            MessageProxy.fromDM(event);
        }
        // CommandManager.runAsClient(event);
    }
}
