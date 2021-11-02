package rsystems.handlers;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import rsystems.Config;
import rsystems.SherlockBot;
import rsystems.commands.botManager.Test;
import rsystems.commands.guildFunctions.*;
import rsystems.commands.modCommands.*;
import rsystems.commands.publicCommands.Help;
import rsystems.commands.subscriberOnly.ColorRole;
import rsystems.commands.subscriberOnly.CopyChannel;
import rsystems.objects.Command;

import java.awt.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class Dispatcher extends ListenerAdapter {

    private final Set<Command> commands = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool = Executors.newCachedThreadPool(newThreadFactory("command-runner", false));

    public Dispatcher() {

        registerCommand(new SelfRole());
        registerCommand(new AutoRole());
        registerCommand(new GiveRole());
        registerCommand(new TakeRole());
        registerCommand(new SoftBan());
        registerCommand(new Infraction());
        registerCommand(new Reason());
        registerCommand(new Test());
        registerCommand(new GuildSetting());
        registerCommand(new CopyChannel());
        registerCommand(new IgnoreChannel());
        registerCommand(new WatchChannel());
        registerCommand(new ColorRole());
        registerCommand(new Help());

    }

    public Set<Command> getCommands() {
        return Collections.unmodifiableSet(new HashSet<>(this.commands));
    }

    public void onGuildMessageReceived(final GuildMessageReceivedEvent event) {

        if (event.getAuthor().isBot()) {
            return;
        }

        final String defaultPrefix = Config.get("defaultPrefix");
        final String message = event.getMessage().getContentRaw();
        final String guildPrefix = SherlockBot.guildMap.get(event.getGuild().getIdLong()).getPrefix();
        final boolean defaultPrefixFound = message.toLowerCase().startsWith(SherlockBot.defaultPrefix.toLowerCase());

        if (defaultPrefixFound || (message.toLowerCase().startsWith(guildPrefix.toLowerCase()))) {
            //PREFIX FOUND

            String prefix;
            if (defaultPrefixFound) {
                prefix = defaultPrefix;
            } else {
                prefix = guildPrefix;
            }

            try {
                if(SherlockBot.database.getLong("IgnoreChannelTable","ChannelID","ChildGuildID",event.getGuild().getIdLong(),"ChannelID",event.getChannel().getIdLong()) == null) {

                    for (final Command c : this.getCommands()) {
                        if (message.toLowerCase().startsWith(prefix.toLowerCase() + c.getName().toLowerCase() + ' ') || message.equalsIgnoreCase(prefix + c.getName())) {
                            this.executeCommand(c, c.getName(), prefix, message, event);
                            return;
                        } else {
                            for (final String alias : c.getAliases()) {
                                if (message.toLowerCase().startsWith(prefix.toLowerCase() + alias.toLowerCase() + ' ') || message.equalsIgnoreCase(prefix + alias)) {
                                    this.executeCommand(c, alias, prefix, message, event);
                                    return;
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPrivateMessageReceived(final PrivateMessageReceivedEvent event) {

        if (event.getAuthor().isBot()) {
            return;
        }

        final Long authorID = event.getAuthor().getIdLong();

        final String prefix = Config.get("defaultPrefix");
        String message = event.getMessage().getContentRaw();

        final MessageChannel channel = event.getChannel();

        if (message.toLowerCase().startsWith(prefix.toLowerCase())) {
            for (final Command c : this.getCommands()) {
                if (message.toLowerCase().startsWith(prefix.toLowerCase() + c.getName().toLowerCase() + ' ') || message.equalsIgnoreCase(prefix + c.getName())) {
                    this.executeCommand(c, c.getName(), prefix, message, event);
                    return;
                } else {
                    for (final String alias : c.getAliases()) {
                        if (message.toLowerCase().startsWith(prefix.toLowerCase() + alias.toLowerCase() + ' ') || message.equalsIgnoreCase(prefix + alias)) {
                            this.executeCommand(c, alias, prefix, message, event);
                            return;
                        }
                    }
                }
            }
        }
    }


    public boolean registerCommand(final Command command) {
        if (command.getName().contains(" "))
            throw new IllegalArgumentException("Name must not have spaces!");
        if (this.commands.stream().map(Command::getName).anyMatch(c -> command.getName().equalsIgnoreCase(c)))
            return false;
        this.commands.add(command);
        return true;
    }

    private void executeCommand(final Command c, final String alias, final String prefix, final String message,
                                final GuildMessageReceivedEvent event) {
        this.pool.submit(() ->
        {
            boolean authorized = false;
            if ((c.getPermissionIndex() == null) && (c.getDiscordPermission() == null)) {
                authorized = true;
            } else {
                authorized = checkAuthorized(c, event.getGuild().getIdLong(), event.getMember(), c.getPermissionIndex());
            }

            if (authorized) {
                try {
                    final String content = this.removePrefix(alias, prefix, message);
                    c.dispatch(event.getAuthor(), event.getChannel(), event.getMessage(), content, event);

                    //database.logCommandUsage(c.getName());
                } catch (final NumberFormatException numberFormatException) {
                    numberFormatException.printStackTrace();
                    event.getMessage().reply("**ERROR:** Bad format received").queue();
                    messageOwner(event, c, numberFormatException);
                } catch (final Exception e) {
                    e.printStackTrace();
                    event.getChannel().sendMessage("**There was an error processing your command!**").queue();
                    messageOwner(event,c,e);
                }
            } else {

                StringBuilder errorString = new StringBuilder();

                if(c.getPermissionIndex() != null){
                    errorString.append("Permission Index: ").append(c.getPermissionIndex()).append("\n");
                }

                if(c.getDiscordPermission() != null){
                    errorString.append("Discord Permission: ").append(c.getDiscordPermission().getName());
                }

                event.getMessage().reply(String.format(event.getAuthor().getAsMention() + " You are not authorized for command: `%s`\n%s", c.getName(), errorString)).queue();
            }
        });
    }

    private void executeCommand(final Command c, final String alias, final String prefix, final String message,
                                final PrivateMessageReceivedEvent event) {
        this.pool.submit(() ->
        {
            boolean authorized = false;

            if(c.getPermissionIndex() == null){
                authorized = true;
            } else {
                authorized = false;
            }

            if (authorized) {

                try {
                    final String content = this.removePrefix(alias, prefix, message);
                    c.dispatch(event.getAuthor(), event.getChannel(), event.getMessage(), content, event);

                    //HiveBot.sqlHandler.logCommandUsage(c.getName());
                } catch (final NumberFormatException numberFormatException) {
                    numberFormatException.printStackTrace();
                    //event.getMessage().reply("**ERROR:** Bad format received").queue();
                    //messageOwner(numberFormatException, c, event);
                } catch (final Exception e) {
                    e.printStackTrace();
                    event.getChannel().sendMessage("**There was an error processing your command!**").queue();
                    //messageOwner(e, c, event);
                }
            } else {
                event.getMessage().reply(String.format(event.getAuthor().getAsMention() + " You are not authorized for command: `%s`\nPermission Index: %d", c.getName(), c.getPermissionIndex())).queue();
            }
        });
    }

    private String removePrefix(final String commandName, final String prefix, String content) {
        content = content.substring(commandName.length() + prefix.length());
        if (content.startsWith(" "))
            content = content.substring(1);
        return content;
    }

    public static ThreadFactory newThreadFactory(String threadName, boolean isdaemon) {
        return (r) ->
        {
            Thread t = new Thread(r, threadName);
            t.setDaemon(isdaemon);
            return t;
        };
    }

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        Command.removeResponses(event.getChannel(), event.getMessageIdLong());
    }

    public Boolean checkAuthorized(final Command c, final Long guildID, final Member member, final Integer permissionIndex){
        boolean authorized = false;

        if(member.hasPermission(Permission.ADMINISTRATOR)){
            return true;
        }

        if(c.getDiscordPermission() != null){
            if(member.getPermissions().contains(c.getDiscordPermission())){
                return true;
            }
        }


        Map<Long, Integer> authmap = SherlockBot.database.getModRoles(guildID);
        for (Role role : member.getRoles()) {

            Long roleID = role.getIdLong();

            if (authmap.containsKey(roleID)) {
                int modRoleValue = authmap.get(roleID);

                /*
                Form a binary string based on the permission level integer found.
                Example: 24 = 11000
                 */
                String binaryString = Integer.toBinaryString(modRoleValue);

                //Reverse the string for processing
                //Example 24 = 11000 -> 00011
                String reverseString = new StringBuilder(binaryString).reverse().toString();

                //Turn the command rank into a binary string
                //Example 8 = 1000
                String binaryIndexString = Integer.toBinaryString(permissionIndex);

                //Reverse the string for lookup
                //Example 8 = 1000 -> 0001
                String reverseLookupString = new StringBuilder(binaryIndexString).reverse().toString();

                int realIndex = reverseLookupString.indexOf('1');

                char indexChar = '0';
                try {

                    indexChar = reverseString.charAt(realIndex);

                } catch (IndexOutOfBoundsException e) {

                } finally {
                    if (indexChar == '1') {
                        authorized = true;
                    }
                }

                if (authorized)
                    break;
            }
        }

        return authorized;
    }

    private void messageOwner(final GuildMessageReceivedEvent event, final Command c, final Exception exception){

        SherlockBot.jda.getUserById(SherlockBot.botOwnerID).openPrivateChannel().queue((channel) -> {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setTitle("System Exception Encountered")
                    .setColor(Color.RED)
                    .addField("Command:",c.getName(),true)
                    .addField("Calling User:",event.getMessage().getAuthor().getAsTag(),true)
                    .addBlankField(true)
                    .addField("Exception:",exception.toString(),false)
                    .setDescription(exception.getCause().getMessage().substring(0,exception.getCause().getMessage().indexOf(":")));

            channel.sendMessage(embedBuilder.build()).queue();

            embedBuilder.clear();
        });
    }

}
