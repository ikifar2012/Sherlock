package rsystems.commands.slashCommands;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import rsystems.Config;
import rsystems.objects.SlashCommand;

public class BotSupport extends SlashCommand {
    @Override
    public boolean isEphemeral() {
        return true;
    }

    @Override
    public void dispatch(User sender, MessageChannel channel, String content, SlashCommandEvent event) {
        event.deferReply(this.isEphemeral()).queue();

        MessageBuilder messageBuilder = new MessageBuilder();
        messageBuilder.setContent(String.format("Hello %s,\n\n" +
                "" +
                "We are sorry to hear you are having trouble.  Please reach out to us via GitHub or our Discord Support Server and we will assist you the best way we can.\n" +
                "Both links are included below.\n\n" +
                "" +
                "Please remember, you can always do `/setup` to get the initial setup instructions posted to your server.",event.getMember().getAsMention()));

        messageBuilder.setActionRows(ActionRow.of(Button.link(Config.get("SUPPORTDISCORDURL"),"Sherlock Support Discord"),Button.link("https://github.com/blade2021/Sherlock/","GitHub")));

        reply(event,messageBuilder.build(),this.isEphemeral());
    }

    @Override
    public String getDescription() {
        return "Get support for Sherlock";
    }
}
