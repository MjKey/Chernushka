package ru.MjKey.chernushka.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import ru.MjKey.chernushka.Chernushka;

public record ToggleChernushkasPayload() implements CustomPayload {
    
    public static final Id<ToggleChernushkasPayload> ID = new Id<>(Identifier.of(Chernushka.MOD_ID, "toggle_chernushkas"));
    
    public static final PacketCodec<RegistryByteBuf, ToggleChernushkasPayload> CODEC = PacketCodec.unit(new ToggleChernushkasPayload());
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
