package rsystems.handlers;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import rsystems.SherlockBot;
import rsystems.commands.slashCommands.*;
import rsystems.objects.Command;
import rsystems.objects.SlashCommand;

import java.awt.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

public class SlashCommandDispatcher extends ListenerAdapter {

    private final Set<SlashCommand> slashCommands = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool = Executors.newCachedThreadPool(newThreadFactory("slashCommand-runner", false));
    private final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(10);

    public SlashCommandDispatcher() {

        //registerCommand(new Apple());
        registerCommand(new Commands());
        registerCommand(new Ban());
        registerCommand(new GuildSetting());
        registerCommand(new SelfRole());
        registerCommand(new AutoRole());
        registerCommand(new Verify());
        registerCommand(new Unban());
        registerCommand(new Moderator());
        registerCommand(new BotSupport());
        registerCommand(new Whitelist());
        registerCommand(new Filter());
    }

    public Set<SlashCommand> getCommands() {
        return Collections.unmodifiableSet(new HashSet<>(this.slashCommands));
    }

    public void onSlashCommand(final SlashCommandEvent event) {

        for (final SlashCommand c : this.getCommands()) {
            if (event.getName().equalsIgnoreCase(c.getName())) {
                this.executeCommand(c, event.getCommandString(), event);
                return;
            }
        }
    }


    public boolean registerCommand(final SlashCommand command) {
        if (command.getName().contains(" "))
            throw new IllegalArgumentException("Name must not have spaces!");
        if (this.slashCommands.stream().map(SlashCommand::getName).anyMatch(c -> command.getName().equalsIgnoreCase(c)))
            return false;
        this.slashCommands.add(command);
        return true;
    }

    private void executeCommand(final SlashCommand c, final String message,
                                final SlashCommandEvent event) {
        this.pool.submit(() ->
        {

            boolean authorized = false;
            if ((c.getPermissionIndex() == null) && (c.getDiscordPermission() == null)) {
                authorized = true;
            } else {
                try {
                    authorized = isAuthorized(c, event.getGuild().getIdLong(), event.getMember(), c.getPermissionIndex());
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }

            if (authorized) {
                try {
                    final String content = message;
                    c.dispatch(event.getUser(), event.getChannel(), content, event);

                    //database.logCommandUsage(c.getName());
                } catch (final NumberFormatException numberFormatException) {
                    numberFormatException.printStackTrace();
                    //event.getMessage().reply("**ERROR:** Bad format received").queue();
                    //messageOwner(event, c, numberFormatException);
                } catch (final Exception e) {
                    e.printStackTrace();
                    event.getHook().sendMessage("**There was an error processing your command!**").queue();
                    //event.getChannel().sendMessage("**There was an error processing your command!**").queue();
                    //messageOwner(event, c, e);
                }
            } else {

                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setColor(Color.decode("#F5661A"));
                embedBuilder.setTitle("Unauthorized Request");
                embedBuilder.setDescription(String.format(" You are not authorized for command: `%s`", c.getName()));

                if (c.getPermissionIndex() != null) {
                    embedBuilder.addField("Mod Permission",c.getPermissionIndex().toString(),true);
                }

                if (c.getDiscordPermission() != null) {
                    embedBuilder.addField("Discord Permission",c.getDiscordPermission().getName(),true);
                }

                event.replyEmbeds(embedBuilder.build()).setEphemeral(false).queue();
            }
        });
    }


    public static ThreadFactory newThreadFactory(String threadName, boolean isdaemon) {
        return (r) ->
        {
            Thread t = new Thread(r, threadName);
            t.setDaemon(isdaemon);
            return t;
        };
    }


    public static Boolean isAuthorized(final SlashCommand c, final Long guildID, final Member member, final Integer permissionIndex) throws SQLException {
        boolean authorized = false;

        if(member.hasPermission(Permission.ADMINISTRATOR)){
            return true;
        }

        if(c.getDiscordPermission() != null){
            if(member.getPermissions().contains(c.getDiscordPermission())){
                return true;
            } else {
                if(c.getPermissionIndex() == null){
                    return false;
                }
            }
        }

        if ((c.getDiscordPermission() == null) && (c.getPermissionIndex() == null)) {
            return true;
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

    private void messageOwner(final MessageReceivedEvent event, final Command c, final Exception exception){

        SherlockBot.jda.getUserById(SherlockBot.botOwnerID).openPrivateChannel().queue((channel) -> {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setTitle("System Exception Encountered")
                    .setColor(Color.RED)
                    .addField("Command:",c.getName(),true)
                    .addField("Calling User:",event.getMessage().getAuthor().getAsTag(),true)
                    .addBlankField(true)
                    .addField("Exception:",exception.toString(),false)
                    .setDescription(exception.getCause().getMessage().substring(0,exception.getCause().getMessage().indexOf(":")));

            channel.sendMessageEmbeds(embedBuilder.build()).queue();

            embedBuilder.clear();
        });
    }

    public void submitCommands(final JDA jda){

            jda.retrieveCommands().queue(commandsList -> {

                ArrayList<String> updateList = new ArrayList<>();

                try {
                    updateList = SherlockBot.database.getList("PushUpdateCommand","CommandName");
                } catch (SQLException e) {
                    e.printStackTrace();
                }


                // DELETE ANY LINGERING COMMANDS THAT ARE NOT PART OF THE APPLICATION ANYMORE FROM THE GUILD COMMAND CACHE
                for(net.dv8tion.jda.api.interactions.commands.Command command:commandsList){
                    Boolean cmdFound = false;

                    for(SlashCommand slashCommand: this.slashCommands){
                        if(slashCommand.getName().equalsIgnoreCase(command.getName())){
                            cmdFound = true;
                            break;
                        }
                    }

                    if(!cmdFound){
                        System.out.println(String.format("REMOVING %s FROM GLOBAL (Command not found in SET)",command.getName()));
                        command.delete().queue();
                    }
                }

                // ADD ANY MISSING COMMANDS TO THE GUILD
                for(SlashCommand slashCommand: this.slashCommands){

                    if(updateList.contains(slashCommand.getName())){
                        jda.upsertCommand(slashCommand.getCommandData()).queue(success -> {
                            System.out.println(String.format("UPDATED COMMAND: %s FOR GLOBAL  NEW ID: %d", success.getName(), success.getIdLong()));
                        });
                    } else {

                        Boolean cmdFound = false;
                        for (net.dv8tion.jda.api.interactions.commands.Command command : commandsList) {
                            if (slashCommand.getName().equalsIgnoreCase(command.getName())) {
                                cmdFound = true;
                                break;
                            }
                        }

                        if (!cmdFound) {
                            System.out.println(String.format("DIDN'T FIND COMMAND: %s FOR GLOBAL", slashCommand.getName()));

                            jda.upsertCommand(slashCommand.getCommandData()).queueAfter(5, TimeUnit.SECONDS, success -> {
                                System.out.println(String.format("UPSERT COMMAND: %s FOR GLOBAL  NEW ID: %d", success.getName(), success.getIdLong()));
                            });
                        }
                    }
                }

            });

    }

}
