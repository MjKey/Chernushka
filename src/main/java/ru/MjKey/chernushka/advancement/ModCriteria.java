package ru.MjKey.chernushka.advancement;

import net.minecraft.advancement.criterion.Criteria;
import ru.MjKey.chernushka.Chernushka;

public class ModCriteria {
    
    public static final ChernushkaCriterion TAME_CHERNUSHKA = Criteria.register(
        Chernushka.MOD_ID + ":tame_chernushka",
        new ChernushkaCriterion()
    );
    
    public static final ChernushkaCriterion BIG_CHERNUSHKA = Criteria.register(
        Chernushka.MOD_ID + ":big_chernushka",
        new ChernushkaCriterion()
    );
    
    public static final ChernushkaCriterion GIANT_CHERNUSHKA = Criteria.register(
        Chernushka.MOD_ID + ":giant_chernushka",
        new ChernushkaCriterion()
    );
    
    public static final ChernushkaCriterion TICKLE_CHERNUSHKA = Criteria.register(
        Chernushka.MOD_ID + ":tickle_chernushka",
        new ChernushkaCriterion()
    );
    
    public static final ChernushkaCriterion CHERNUSHKA_ARMY = Criteria.register(
        Chernushka.MOD_ID + ":chernushka_army",
        new ChernushkaCriterion()
    );
    
    public static void register() {
        Chernushka.LOGGER.info("Registering advancement criteria...");
    }
}
