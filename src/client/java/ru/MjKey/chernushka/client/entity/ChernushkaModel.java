package ru.MjKey.chernushka.client.entity;

import net.minecraft.util.Identifier;
import ru.MjKey.chernushka.Chernushka;
import ru.MjKey.chernushka.entity.ChernushkaEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.animation.state.ControllerState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class ChernushkaModel extends GeoModel<ChernushkaEntity> {
    
    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        // GeckoLib 5 автоматически ищет в geckolib/models/
        return Identifier.of(Chernushka.MOD_ID, "chernushka");
    }
    
    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return Identifier.of(Chernushka.MOD_ID, "textures/entity/texture.png");
    }
    
    @Override
    public Identifier getAnimationResource(ChernushkaEntity animatable) {
        // GeckoLib 5 автоматически ищет в geckolib/animations/
        return Identifier.of(Chernushka.MOD_ID, "chernushka");
    }
}
