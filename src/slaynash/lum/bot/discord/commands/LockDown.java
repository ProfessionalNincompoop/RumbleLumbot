package slaynash.lum.bot.discord.commands;

import java.util.Collections;
import java.util.Objects;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import slaynash.lum.bot.ConfigManager;
import slaynash.lum.bot.discord.Command;
import slaynash.lum.bot.discord.CommandManager;
import slaynash.lum.bot.discord.GuildConfigurations;
import slaynash.lum.bot.discord.Moderation;
import slaynash.lum.bot.discord.utils.CrossServerUtils;

public class LockDown extends Command {

    @Override
    protected void onServer(String paramString, MessageReceivedEvent event) {
        if (!includeInHelp(event))
            return;

        if (event.getGuild().getPublicRole().hasPermission(Permission.MESSAGE_SEND)) {
            event.getChannel().sendMessage("@everyone has send message and lockdown will not work").setAllowedMentions(Collections.emptyList()).queue();
            return;
        }

        String reportChannel = CommandManager.mlReportChannels.get(event.getGuild().getIdLong());
        Long lockDownRoleID = GuildConfigurations.lockDownRoles.get(event.getGuild().getIdLong());
        if (lockDownRoleID == null) {
            event.getChannel().sendMessage("LockDown is not setup in this server. Please DM rakosi2#0001 to setup LockDown").setAllowedMentions(Collections.emptyList()).queue();
        }
        Role lockDownRole = event.getGuild().getRoleById(lockDownRoleID);
        if (!event.getGuild().getSelfMember().canInteract(lockDownRole)) {
            event.getChannel().sendMessage("I can not interact with the role " + lockDownRole.getName()).queue();
            return;
        }
        boolean lockDownState = lockDownRole.hasPermission(Permission.MESSAGE_SEND);

        if (lockDownState)
            lockDownRole.getManager().revokePermissions(Permission.MESSAGE_SEND).complete();
        else
            lockDownRole.getManager().givePermissions(Permission.MESSAGE_SEND).complete();

        if (!Objects.equals(reportChannel, event.getChannel().asGuildMessageChannel().getId()))
            event.getGuild().getTextChannelById(reportChannel).sendMessage("User " + event.getAuthor().getEffectiveName() + " has " + (lockDownState ? "locked down" : "unlocked") + " this server in " + event.getChannel().getName()).queue();
        event.getChannel().sendMessage("User " + event.getAuthor().getEffectiveName() + " has " + (lockDownState ? "locked down" : "unlocked") + " this server.").queue();
    }

    @Override
    protected boolean matchPattern(String paramString) {
        return paramString.startsWith(ConfigManager.discordPrefix + getName());
    }

    @Override
    public boolean includeInHelp(MessageReceivedEvent event) {
        return GuildConfigurations.lockDownRoles.get(event.getGuild().getIdLong()) != null && (Moderation.getAdmins(event.getGuild()).contains(event.getAuthor().getIdLong()) || CrossServerUtils.checkIfStaff(event));
    }

    @Override
    public String getHelpDescription() {
        return "Toggles Member's sending messages permission in all channels for emergencies - Staff only";
    }

    @Override
    public String getName() {
        return "lockdown";
    }
}
