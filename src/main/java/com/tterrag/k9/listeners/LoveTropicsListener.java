package com.tterrag.k9.listeners;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.K9;
import com.tterrag.k9.util.RequestHelper;
import com.tterrag.k9.util.SaveHelper;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IPrivateChannel;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.util.RequestBuffer;

@RequiredArgsConstructor
public class LoveTropicsListener {
    
    @Value
    private static class Donation {
        int id;
        double amount;
        @SerializedName("display_name")
        String name;
        String email;
    }
    
    private enum State {
        NONE,
        REJECTED,
        PENDING,
        VERIFIED,
        ACCEPTED,
        WHITELISTED,
        ;
    }
    
    @Value
    @RequiredArgsConstructor
    private static class Data {
        @NonFinal
        @Setter
        volatile long message;
        Map<Long, State> userStates = Maps.newConcurrentMap();
        Map<Long, String> verifiedEmails = Maps.newConcurrentMap();
        Map<Long, Set<String>> attemptedEmails = Maps.newConcurrentMap();
        Map<Long, Integer> resets = Maps.newConcurrentMap();
    }
    
    private static final ExecutorService EMAIL_CHECKER = Executors.newSingleThreadExecutor();
    
    private static final Pattern MAYBE_EMAIL = Pattern.compile("\\S+@\\S+\\.\\w+");
    
    private static final Gson GSON = new Gson();
    
    private final SaveHelper<Data> saveHelper = new SaveHelper<>(new File("lovetropics"), new Gson(), new Data());
    
    private final Data data = saveHelper.fromJson("data.json", Data.class);
    
    private final long channel = 510113971487899648L; // #verify-donation
    private final long adminRole = 444888468078985227L; // Admin
    private final long donorRole = 444894142217191424L; // LT18 Donor
    private final long whitelistRole = 510254529015578635L; // LT18 Server Member
    private final long whitelistChannel = 510331459329064970L; // #server-relay
    
    private final String key;
    private final int minDonation;
    
    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent event) {
        long author = event.getAuthor().getLongID();
        if (event.getChannel().isPrivate()) {
            State state = data.getUserStates().getOrDefault(author, State.NONE);
            if (state == State.PENDING || state == State.VERIFIED) {
                final String email;
                int triesTmp = -1; // Where this is printed will never run if it's not set later on
                if (state == State.PENDING) {
                    email = event.getMessage().getContent().trim();
                    if (MAYBE_EMAIL.matcher(email).matches()) {
                        Set<String> prevEmails = data.getAttemptedEmails().computeIfAbsent(author, $ -> Sets.newConcurrentHashSet());
                        triesTmp = data.getResets().merge(author, prevEmails.contains(email) ? 0 : 1, (i1, i2) -> Math.min(999, i1 + i2));
                        if (triesTmp > 3) {
                            RequestBuffer.request(() -> event.getChannel().sendMessage("Sorry, you are out of email attempts."));
                            save();
                            return;
                        }
                        if (triesTmp < 100) { // In case of spammer...that's enough
                            data.getAttemptedEmails().get(author).add(email);
                        }
                    } else {
                        RequestBuffer.request(() -> event.getChannel().sendMessage("That doesn't look like a valid email. Please try again."));
                        return;
                    }
                } else {
                    email = data.getVerifiedEmails().get(author);
                }
                save();

                final int tries = triesTmp;
                CompletableFuture.supplyAsync(() -> getTotalDonations((IPrivateChannel) event.getChannel(), email), EMAIL_CHECKER)
                     .thenAccept(total -> {
                         if (total > 0) {
                             NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.US);
                             IRole role = RequestBuffer.request(() -> event.getClient().getRoleByID(donorRole)).get();
                             RequestBuffer.request(() -> event.getAuthor().addRole(role));
                             RequestBuffer.request(() -> event.getChannel().sendMessage("Your email was verified! Donation amount: " + fmt.format(total))).get();
                             data.getVerifiedEmails().put(author, email);
                             if (total >= minDonation) {
                                 RequestBuffer.request(() -> event.getChannel().sendMessage("Congratulations! This amount qualifies for server access. Reply with your Minecraft in-game name to be whitelisted."));
                                 data.getUserStates().put(author, State.ACCEPTED);
                             } else {
                                 RequestBuffer.request(() -> event.getChannel().sendMessage("Unfortunately, this is not enough to qualify for server access. However, you have still been assigned the donor role!\n\nYou need at least " + fmt.format(minDonation) + " across all donations to qualify.\n**Say anything in this chat to try again.**"));
                                 data.getUserStates().put(author, State.VERIFIED);
                             }
                             save();
                         } else {
                             RequestBuffer.request(() -> event.getChannel().sendMessage("Sorry, there were no donations by that email. Either the email was incorrect, or you have not donated yet.\n\nYou may try **" + (3 - tries) + "** more times to enter the correct email, or enter the same email again to re-attempt."));
                         }
                     });
                
            } else if (state == State.ACCEPTED) {
                String username = event.getMessage().getContent().trim();
                RequestBuffer.request(() -> event.getChannel().sendMessage("Whitelisted `" + username + "`\n\nHave fun!"));
                IRole role = RequestBuffer.request(() -> event.getClient().getRoleByID(whitelistRole)).get();
                IChannel channel = RequestBuffer.request(() -> event.getClient().getChannelByID(whitelistChannel)).get();
                RequestHelper.requestOrdered(
                        () -> event.getAuthor().addRole(role),
                        () -> channel.sendMessage("!whitelist add " + username));
                data.getUserStates().put(author, State.WHITELISTED);
                save();
            }
        } else {
            List<IRole> roles = RequestBuffer.request(() -> event.getAuthor().getRolesForGuild(event.getGuild())).get();
            if (event.getChannel().getLongID() == channel && roles.stream().mapToLong(IRole::getLongID).anyMatch(l -> l == adminRole)) {
                if (event.getMessage().getContent().equals("refresh")) {
                    RequestBuffer.request(() -> event.getChannel().getFullMessageHistory().bulkDelete()).get(); // Wait for completion
                    
                    IMessage msg = RequestBuffer.request(() -> event.getChannel().sendMessage("React to this message to verify your donation and get your roles/whitelist.")).get();
                    RequestBuffer.request(() -> msg.addReaction(ReactionEmoji.of("\uD83D\uDCB8")));
                    
                    data.setMessage(msg.getLongID());
                    save();
                }
            }
        }
    }
    
    private double getTotalDonations(IPrivateChannel channel, String email) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL("https://lovetropics.com/payments?per_page=99999").openConnection();
            
            con.addRequestProperty("LTKEY", key);
            
            int code = con.getResponseCode();
            
            if (code / 100 != 2) {
                throw new IOException(IOUtils.readLines(con.getErrorStream(), Charsets.UTF_8).stream().collect(Collectors.joining("\n")));
            }
            
            JsonObject res = GSON.fromJson(IOUtils.readLines(con.getInputStream(), Charsets.UTF_8).stream().collect(Collectors.joining("\n")), JsonObject.class);
            JsonArray payments = res.get("payments").getAsJsonArray();
            
            List<Donation> donations = GSON.fromJson(payments, new TypeToken<List<Donation>>() {}.getType());
            return donations.stream()
                            .filter(d -> d.getEmail().equalsIgnoreCase(email))
                            .reduce(0D, (tot, d) -> tot + d.getAmount(), (d1, d2) -> d1 + d2);
            
        } catch (Exception e) {
            RequestBuffer.request(() -> channel.sendMessage(e.getClass().getSimpleName() + ": " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
    
    @EventSubscriber
    public void onReactAdd(ReactionAddEvent event) {
        if (event.getMessageID() == data.getMessage() && event.getUser().getLongID() != K9.instance.getOurUser().getLongID() && !data.getUserStates().containsKey(event.getUser().getLongID())) {
            IPrivateChannel pm = RequestBuffer.request(() -> event.getUser().getOrCreatePMChannel()).get();
            if (pm == null) {
                return;
            }
            RequestBuffer.request(() -> pm.sendMessage("To verify your donation, please reply with the email you used to donate."));
            data.getUserStates().put(event.getUser().getLongID(), State.PENDING);
            save();
        }
    }

    private final synchronized void save() {
        saveHelper.writeJson("data.json", data);
    }
}
