package ru.MjKey.chernushka.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import ru.MjKey.chernushka.Chernushka;

/**
 * Пакет S2C для активации локатора чернушек на клиенте.
 * @param found - найдена ли дикая чернушка
 * @param distance - расстояние до ближайшей (0 если не найдена)
 * @param directionX - направление X к чернушке
 * @param directionZ - направление Z к чернушке
 * @param maxDistance - максимальная дистанция поиска
 */
public record ChernushkaLocatorPayload(
    boolean found,
    float distance,
    float directionX,
    float directionZ,
    float maxDistance
) implements CustomPayload {
    
    public static final Id<ChernushkaLocatorPayload> ID = new Id<>(
        Identifier.of(Chernushka.MOD_ID, "chernushka_locator")
    );
    
    public static final PacketCodec<RegistryByteBuf, ChernushkaLocatorPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.BOOLEAN, ChernushkaLocatorPayload::found,
        PacketCodecs.FLOAT, ChernushkaLocatorPayload::distance,
        PacketCodecs.FLOAT, ChernushkaLocatorPayload::directionX,
        PacketCodecs.FLOAT, ChernushkaLocatorPayload::directionZ,
        PacketCodecs.FLOAT, ChernushkaLocatorPayload::maxDistance,
        ChernushkaLocatorPayload::new
    );
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
