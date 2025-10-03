package com.pallux.practicebot.bot;

import net.citizensnpcs.api.trait.Trait;

// This is a "marker" trait. Its only purpose is to be attached to our NPCs
// so we can identify them as belonging to PracticeBot, even after a server restart.
public class PracticeBotTrait extends Trait {

    public PracticeBotTrait() {
        super("practicebottrait");
    }

    // No event handlers are needed here as this is just a marker.
}