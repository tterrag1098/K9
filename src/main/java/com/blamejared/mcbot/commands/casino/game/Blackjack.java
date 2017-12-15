package com.blamejared.mcbot.commands.casino.game;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import com.blamejared.mcbot.commands.casino.util.cards.Card;
import com.blamejared.mcbot.commands.casino.util.cards.Deck;
import com.blamejared.mcbot.commands.casino.util.cards.Hand;
import com.blamejared.mcbot.commands.casino.util.cards.Hand.SimpleHand;
import com.blamejared.mcbot.commands.casino.util.cards.MultiDeck;
import com.blamejared.mcbot.commands.casino.util.cards.Value;
import com.blamejared.mcbot.util.BakedMessage;
import com.google.common.base.Joiner;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IUser;

public class Blackjack implements Game<Blackjack> {

    @RequiredArgsConstructor
    public enum BlackjackAction implements GameAction<Blackjack> {
        HIT("hit", (bj, h) -> bj.getDeck().deal(h)),
        STAY("stay", (bj, h) -> h.stand()),
        SPLIT("split", (bj, h) -> {
            // Get current hand index and remove it
            List<HandBlackjack> hands = bj.getPlayerHands().get(bj.getActiveUser());
            int idx = hands.indexOf(h);
            hands.remove(idx);
            
            // Create a "deck" from the active hand to deal it to the split hands
            Deck temp = new Deck(h.getCards());
            
            // Create two new hands, and deal one card from the temp deck, and one from the real deck, to both
            // Then add them to the player hand list in the spot where the unsplit hand was
            HandBlackjack h2 = bj.new HandBlackjack();
            temp.deal(h2);
            bj.getDeck().deal(h2);
            hands.add(idx, h2);
            h2 = bj.new HandBlackjack();
            temp.deal(h2);
            bj.getDeck().deal(h2);
            hands.add(idx + 1, h2);
            
            // Update the active hand
            bj.activeHand = hands.get(idx);
        }),
        DOUBLE_DOWN("double", (bj, h) -> {
            bj.getDeck().deal(h);
            h.stand();
        }),
        ;
        
        private final String name;
        private final BiConsumer<Blackjack, HandBlackjack> function;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void accept(Blackjack t) {
            function.accept(t, t.getActiveHand());
            t.onAction();
        }
    }
    
    private class HandBlackjack extends SimpleHand {
        
        private boolean stood;
        
        public void stand() {
            this.stood = true;
        }
        
        public boolean canHit() {
            return !stood && getHandValue() < 21;
        }
          
        public int getHandValue() {
            int value = 0;
            for (Card c : getCards()) {
                value += getCardValue(c);
            }
            if (value <= 11) {
                long aceCount = getCards().stream().filter(c -> c.getValue() == Value.ACE).count();
                while (value <= 11 && aceCount --> 0) {
                    value += 10;
                }
            }
            return value;
        }

        private int addValue(Card card, int currentValue) {
            currentValue += getCardValue(card);
            if (currentValue <= 11 && card.getValue() == Value.ACE) {
                currentValue += 10;
            }
            return currentValue;
        }

        @Override
        public String toString() {
            return getCards().toString() + " => " + (getHandValue() == 21 && size() == 2 ? "BLACKJACK" : getHandValue() > 21 ? "BUST (" + getHandValue() + ")" : getHandValue());
        }
    }
    
    @Getter
    private final IChannel channel;

    // special game rules
    private final int deckcount;
    private final boolean soft17;
    
    // the current dealing deck
    @Getter
    private final Deck deck;
    // the used cards
    private final Deck discards = new Deck(new ArrayList<>());
    
    private final List<IUser> usersAtTable = new ArrayList<>();
    
    @Getter
    private final HandBlackjack dealerHand = new HandBlackjack();
    @Getter
    private final Map<IUser, List<HandBlackjack>> playerHands = new HashMap<>();
    
    @Getter
    private IUser activeUser;
    @Getter
    private HandBlackjack activeHand;
    
    public Blackjack(IChannel channel, int decks, boolean soft17) {
        this.channel = channel;
        this.deckcount = decks; // stored for intro text

        this.deck = new MultiDeck(decks);
        this.deck.shuffle();

        this.soft17 = soft17;
    }
    
    @Override
    public void restart() {
        getDealerHand().returnTo(discards);
        playerHands.values().stream().flatMap(List::stream).forEach(h -> h.returnTo(discards));
        
        if (discards.getCards().size() > deck.getCards().size()) {
            deck.merge(discards);
            deck.shuffle();
        }
        
        if (deck.getCards().size() + discards.getCards().size() % 52 != 0) {
            throw new IllegalStateException("Missing cards!");
        }
        
        getPlayerHands().clear();
        for (IUser user : usersAtTable) {
            playerHands.computeIfAbsent(user, u -> new ArrayList<>()).add(new HandBlackjack());
        }
        
        this.activeUser = usersAtTable.get(0);
        this.activeHand = getPlayerHands().get(usersAtTable.get(0)).get(0);

        for (int i = 0; i < 2; i++) {
            for (List<HandBlackjack> decks : getPlayerHands().values()) {
                deck.deal(decks.get(0));
            }
            deck.deal(getDealerHand());
        }
        
        // Check if the player, or the dealer, got blackjack
        if (getActiveHand().getHandValue() == 21 || getDealerHand().getHandValue() == 21) {
            nextHand();
            onAction();
        }
    }
    
    @Override
    public boolean addUser(IUser user) {
        if (this.usersAtTable.contains(user)) {
            return false;
        }
        this.usersAtTable.add(user);
        return true;
    }
    
    public void onAction() {
        if (getActiveHand() != getDealerHand()) {
            if (!getActiveHand().canHit()) {
                nextHand();
                if (getActiveHand() == getDealerHand()) {
                    while (getDealerHand().getHandValue() < 17 || (soft17 && getDealerHand().getHandValue() < 18 && getDealerHand().getCards().stream().map(Card::getValue).anyMatch(v -> v == Value.ACE))) {
                        BlackjackAction.HIT.accept(this);
                    }
                }
            }
        }
    }
    
    @Override
    public BakedMessage getIntro() {
        StringBuilder sb = new StringBuilder();
        sb.append("==============================================\n");
        sb.append("The game is blackjack. The goal: 21 or bust.\n");
        sb.append("There are ").append(deckcount).append(" decks in play. Shuffles happen at 50%\n");
        sb.append("==============================================");
        return new BakedMessage().withContent(sb.toString());
    }
    
    @Override
    public BakedMessage getGameState() {
        StringBuilder handstr = new StringBuilder();
        handstr.append(Optional.ofNullable(getActiveUser().getNicknameForGuild(getChannel().getGuild())).orElse(getActiveUser().getName())).append("'s hand(s): ");
        handstr.append(Joiner.on(", ").join(playerHands.get(getActiveUser()).stream().map(h -> h == getActiveHand() ? "**" + h + "**" : h.toString()).toArray(String[]::new)));
        handstr.append('\n');
        
        if (getActiveHand() == getDealerHand()) {
            handstr.append("Dealer hand: ").append(getDealerHand());
        } else {
            handstr.append("Dealer shows: ").append(getDealerHand().getCards().get(0));
        }
        
        return new BakedMessage().withContent(handstr.toString());
    }

    @Override
    public Set<BlackjackAction> getPossibleActions() {
        if (getActiveHand() == getDealerHand()) {
            return EnumSet.noneOf(BlackjackAction.class);
        }
        Set<BlackjackAction> ret = EnumSet.of(BlackjackAction.HIT, BlackjackAction.STAY);
        if (getActiveHand().size() == 2) {
            ret.add(BlackjackAction.DOUBLE_DOWN);
            List<Card> handCards = getActiveHand().getCards();
            if (getCardValue(handCards.get(0)) == getCardValue(handCards.get(1))) {
                ret.add(BlackjackAction.SPLIT);
            }
        }
        return ret;
    }
    
    protected void nextHand() {
        List<HandBlackjack> hands = getPlayerHands().get(getActiveUser());
        int index = hands.indexOf(getActiveHand());
        if (index == -1) {
            throw new IllegalStateException("Cannot move to the next hand from the dealer's hand!");
        }
        if (index == hands.size() - 1) {
            index = usersAtTable.lastIndexOf(getActiveUser());
            if (index == usersAtTable.size() - 1) {
                activeHand = getDealerHand();
            } else {
                activeHand = getPlayerHands().get(usersAtTable.get(index + 1)).get(0);
            }
        } else {
            activeHand = hands.get(index + 1);
        }
    }

    protected final int getCardValue(Card card) {
        return card.getValue().isFace() ? 10 : card.getValue().numeric();
    }
}
