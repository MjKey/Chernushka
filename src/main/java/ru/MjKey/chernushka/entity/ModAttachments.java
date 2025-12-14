package ru.MjKey.chernushka.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;
import ru.MjKey.chernushka.Chernushka;

import java.util.ArrayList;
import java.util.List;

public class ModAttachments {
    
    // Данные скрытой чернушки
    public record HiddenChernushkaData(int mergeLevel) {
        public static final Codec<HiddenChernushkaData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.INT.fieldOf("mergeLevel").forGetter(HiddenChernushkaData::mergeLevel)
            ).apply(instance, HiddenChernushkaData::new)
        );
    }
    
    // Attachment для хранения UUID владельца чернушки
    public static final AttachmentType<String> OWNER_UUID = AttachmentRegistry.createPersistent(
        Identifier.of(Chernushka.MOD_ID, "owner_uuid"),
        Codec.STRING
    );
    
    // Attachment для хранения уровня слияния чернушки
    public static final AttachmentType<Integer> MERGE_LEVEL = AttachmentRegistry.createPersistent(
        Identifier.of(Chernushka.MOD_ID, "merge_level"),
        Codec.INT
    );
    
    // Attachment для хранения скрытых чернушек игрока
    public static final AttachmentType<List<HiddenChernushkaData>> HIDDEN_CHERNUSHKAS = AttachmentRegistry.createPersistent(
        Identifier.of(Chernushka.MOD_ID, "hidden_chernushkas"),
        HiddenChernushkaData.CODEC.listOf()
    );
    
    public static void register() {
        Chernushka.LOGGER.info("Registering attachments...");
    }
}
