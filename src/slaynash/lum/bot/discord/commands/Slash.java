package slaynash.lum.bot.discord.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import slaynash.lum.bot.discord.CommandManager;
import slaynash.lum.bot.discord.ExceptionUtils;
import slaynash.lum.bot.discord.GuildConfigurations;
import slaynash.lum.bot.discord.GuildConfigurations.ConfigurationMap;

public class Slash {
    public static void slashRun(SlashCommandEvent event){
        if (event.getName().equals("config")) { // Guild command
            String guildID = event.getGuild().getId();
            sendReply(event, guildID);
        }else if (event.getName().equals("configs")) { //Global/DM command
            String guildID = event.getOptionsByName("guild").get(0).getAsString();
            sendReply(event, guildID);
        }
    }

    public static void buttonUpdate(ButtonClickEvent event){
        try {
            String[] message = event.getMessage().getContentRaw().split(": ");
            if (message.length < 2) {
                event.deferEdit().queue();
                return;
            }
            Long guildID = Long.valueOf(message[message.length - 1]);
            Boolean[] config = GuildConfigurations.configurations.get(guildID);
            if (event.getUser().getId().equals(event.getJDA().getGuildById(guildID).getOwnerId()) || event.getUser().getId().equals("145556654241349632" /*Slaynash*/) || event.getUser().getId().equals("240701606977470464" /*rakosi2*/)) {
                switch(event.getComponentId()) {
                case ("ss") :
                    config[ConfigurationMap.SCAMSHIELD.ordinal()] = !config[ConfigurationMap.SCAMSHIELD.ordinal()];
                    event.editButton(config[ConfigurationMap.SCAMSHIELD.ordinal()] ? Button.success("ss", "Scam Shield") : Button.danger("ss", "Scam Shield")).queue();
                    break;
                case ("dll") :
                    config[ConfigurationMap.DLLREMOVER.ordinal()] = !config[ConfigurationMap.DLLREMOVER.ordinal()];
                    event.editButton(config[ConfigurationMap.DLLREMOVER.ordinal()] ? Button.success("dll", "DLL Remover") : Button.danger("dll", "DLL Remover")).queue();
                    break;
                case ("reaction") :
                    config[ConfigurationMap.LOGREACTION.ordinal()] = !config[ConfigurationMap.LOGREACTION.ordinal()];
                    event.editButton(config[ConfigurationMap.LOGREACTION.ordinal()] ? Button.success("reaction", "Log Reactions") : Button.danger("reaction", "Log Reactions")).queue();
                    break;
                case ("thanks") :
                    config[ConfigurationMap.LUMREPLIES.ordinal()] = !config[ConfigurationMap.LUMREPLIES.ordinal()];
                    event.editButton(config[ConfigurationMap.LUMREPLIES.ordinal()] ? Button.success("thanks", "Chatty Lum") : Button.danger("thanks", "Chatty Lum")).queue();
                    break;
                case ("partial") :
                    config[ConfigurationMap.PARTIALLOGREMOVER.ordinal()] = !config[ConfigurationMap.PARTIALLOGREMOVER.ordinal()];
                    event.editButton(config[ConfigurationMap.PARTIALLOGREMOVER.ordinal()] ? Button.success("partial", "Partial Log remover") : Button.danger("partial", "Partial Log remover")).queue() ;
                    break;
                case ("general") :
                    config[ConfigurationMap.GENERALLOGREMOVER.ordinal()] = !config[ConfigurationMap.GENERALLOGREMOVER.ordinal()];
                    event.editButton(config[ConfigurationMap.GENERALLOGREMOVER.ordinal()] ? Button.success("general", "General Log remover") : Button.danger("general", "General Log remover")).queue();
                    break;
                case ("delete") :
                    event.getMessage().delete().queue();
                    break;
                default :
                }
                GuildConfigurations.configurations.put(guildID, config); // update Values
                CommandManager.saveGuildConfigs(); // backup values
            }else{
                event.deferEdit().queue();
            }
        } catch (Exception e) {
            ExceptionUtils.reportException("An error has occured while updating buttons:", e);
        }
    }

    private static void sendReply(SlashCommandEvent event, String guildID){
        try {
            Guild guild = event.getJDA().getGuildById(guildID);
            Boolean[] config = GuildConfigurations.configurations.get(Long.valueOf(guildID));
            if (config == null) {
                config = new Boolean[] {false,false,false,false,false,false};
                GuildConfigurations.configurations.put(Long.valueOf(guildID), config);
                CommandManager.saveGuildConfigs();
            }
            if (guild != null){
                if (event.getUser().getId().equals(guild.getOwnerId()) || event.getUser().getId().equals("145556654241349632" /*Slaynash*/) || event.getUser().getId().equals("240701606977470464" /*rakosi2*/)) {
                    event.reply("Server Config for " + guild.getName() + ": " + guildID)
                        .addActionRow(
                            config[ConfigurationMap.SCAMSHIELD.ordinal()] ? Button.success("ss", "Scam Shield") : Button.danger("ss", "Scam Shield"),
                            config[ConfigurationMap.LOGREACTION.ordinal()] ? Button.success("reaction", "Log Reactions") : Button.danger("reaction", "Log Reactions"),
                            config[ConfigurationMap.LUMREPLIES.ordinal()] ? Button.success("thanks", "Chatty Lum") : Button.danger("thanks", "Chatty Lum"))
                        .addActionRow(
                            config[ConfigurationMap.DLLREMOVER.ordinal()] ? Button.success("dll", "DLL Remover") : Button.danger("dll", "DLL Remover"),
                            config[ConfigurationMap.PARTIALLOGREMOVER.ordinal()] ? Button.success("partial", "Partial Log remover") : Button.danger("partial", "Partial Log remover"),
                            config[ConfigurationMap.GENERALLOGREMOVER.ordinal()] ? Button.success("general", "General Log remover") : Button.danger("general", "General Log remover"))
                        .addActionRow(
                            Button.danger("delete", "Delete this message")).queue();
                } else event.reply("You do not have permission to use this command.");
            } else event.reply("Guild not found.");
        } catch (Exception e) {
            ExceptionUtils.reportException("An error has occured while sending Slash Reply:", e);
        }
    }
}