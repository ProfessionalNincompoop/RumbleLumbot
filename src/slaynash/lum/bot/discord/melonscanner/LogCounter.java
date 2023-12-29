package slaynash.lum.bot.discord.melonscanner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;

import net.dv8tion.jda.api.JDA.Status;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message.Attachment;
import slaynash.lum.bot.ConfigManager;
import slaynash.lum.bot.discord.JDAManager;
import slaynash.lum.bot.utils.ExceptionUtils;

public final class LogCounter {  //TODO: create directory if it doesn't exist

    private static final String workingPath = System.getProperty("user.dir");
    private static int previousLogCount = 0;
    private static int previousSSCount = 0;

    public static File addMLCounter(Attachment attachment) {
        try {
            String directoryPath = workingPath + "/MLlogs/";
            File att = attachment.getProxy().downloadToFile(new File(directoryPath + Instant.now().toString().replace(":", "_") + "-" + attachment.getFileName())).get();
            System.out.println("Downloaded attachment to " + att.getCanonicalPath());
            return att;
        }
        catch (Exception exception) {
            ExceptionUtils.reportException(
                "Exception while Saving ML Log",
                exception.getMessage(),
                exception);
        }
        return null;
    }

    public static void addSSCounter(String bannedUser, String message, String guildID) {
        try {
            String directoryPath = workingPath + "/SSlogs/";

            Files.writeString(Path.of(directoryPath, bannedUser + "-" + guildID + ".txt"), message);
        }
        catch (Exception exception) {
            ExceptionUtils.reportException(
                "Exception while Saving Scam Shield Log",
                exception.getMessage(),
                exception);
        }
    }

    public static void updateCounter() {
        try {
            if (!JDAManager.isEventsEnabled()) return;
            if (JDAManager.getJDA() == null || JDAManager.getJDA().getStatus() != Status.CONNECTED)
                return;
            Date date = new Date();
            String directoryPath = workingPath + "/MLlogs/";
            File directory = new File(directoryPath);

            int logCount = directory.listFiles().length;
            if (logCount > 0) {
                // remove folders that is older then 24 hours
                for (File fileEntry : directory.listFiles()) {
                    if ((date.getTime() - fileEntry.lastModified()) > 24 * 60 * 60 * 1000) {
                        fileEntry.delete();
                    }
                }
            }
            logCount = directory.listFiles().length;

            directoryPath = workingPath + "/SSlogs/";
            directory = new File(directoryPath);
            int sslogCount = directory.listFiles().length;
            if (sslogCount > 0) {
                // remove folders that is older then 24 hours
                for (File fileEntry : directory.listFiles()) {
                    if ((date.getTime() - fileEntry.lastModified()) > 24 * 60 * 60 * 1000) {
                        fileEntry.delete();
                    }
                }
            }
            sslogCount = directory.listFiles().length;

            if (logCount != previousLogCount || sslogCount != previousSSCount)
                JDAManager.getJDA().getPresence().setActivity(Activity.watching((ConfigManager.mainBot ? "" : "BACKUP ") + logCount + " melons squashed and removed "
                    + sslogCount + " scammers in 24 hours. In " + JDAManager.getJDA().getGuilds().size() + " guilds!"));
            previousLogCount = logCount;
            previousSSCount = sslogCount;
        }
        catch (Exception exception) {
            ExceptionUtils.reportException(
                "Exception while Updating Counter",
                exception);
        }
    }
}
