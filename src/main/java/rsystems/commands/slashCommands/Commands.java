package rsystems.commands.slashCommands;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import rsystems.SherlockBot;
import rsystems.handlers.Dispatcher;
import rsystems.handlers.SlashCommandDispatcher;
import rsystems.objects.Command;
import rsystems.objects.SlashCommand;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;

public class Commands extends SlashCommand {
    @Override
    public boolean isEphemeral() {
        return true;
    }

    @Override
    public void dispatch(User sender, MessageChannel channel, String content, SlashCommandEvent event) {
        event.deferReply(this.isEphemeral()).queue();


        ArrayList<String> commandList = new ArrayList<>();
        for(Command c: SherlockBot.dispatcher.getCommands()){
            try {
                if(Dispatcher.isAuthorized(c,event.getGuild().getIdLong(),event.getMember(),c.getPermissionIndex())){
                    commandList.add(c.getName());
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        ArrayList<String> slashCMDList = new ArrayList<>();
        for(SlashCommand c: SherlockBot.slashCommandDispatcher.getCommands()){
            try {
                if(SlashCommandDispatcher.isAuthorized(c,event.getGuild().getIdLong(),event.getMember(),c.getPermissionIndex())){
                    slashCMDList.add(c.getName());
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        //Sort commands into alphabetical order.  You're welcome SG.
        commandList.sort(Comparator.naturalOrder());
        slashCMDList.sort(Comparator.naturalOrder());

        if((commandList.size() == 0) && (slashCMDList.size() == 0)){
            reply(event,"You have no commands available",this.isEphemeral());
        } else {

            StringBuilder commandsString = new StringBuilder();
            if(commandList.size() > 0){
                commandsString.append("`Chat Commands:` \n").append(commandList.toString().replaceFirst("\\[","").replace("]","")).append("\n\n");
            }

            if(slashCMDList.size() > 0){
                commandsString.append("`Slash Commands:` \n").append(slashCMDList.toString().replaceFirst("\\[","").replace("]",""));
            }

            reply(event,commandsString.toString(),this.isEphemeral());

        }
    }

    @Override
    public String getDescription() {
        return "Return a list of available commands for your user account";
    }

}
