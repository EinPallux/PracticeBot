package com.pallux.practicebot.bot;

import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;

/**
 * This is a "marker" trait. Its only purpose is to be attached to our NPCs
 * so we can identify them as belonging to PracticeBot, even after a server restart.
 *
 * Citizens will automatically save and load this trait, allowing us to identify
 * and clean up our bots on server startup.
 */
@TraitName("practicebottrait")
public class PracticeBotTrait extends Trait {

    public PracticeBotTrait() {
        super("practicebottrait");
    }

    // No event handlers or additional logic needed - this is purely a marker trait
    // that Citizens will persist across restarts, allowing us to identify our bots.
}