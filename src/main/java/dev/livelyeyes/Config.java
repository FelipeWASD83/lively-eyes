package dev.livelyeyes;

import dev.livelyeyes.logic.ExpressionController;
import net.neoforged.neoforge.common.ModConfigSpec;

/** config/lively_eyes-client.toml. Values are read every frame, so changes apply with the game open. */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Liga ou desliga todas as expressões.")
            .define("enabled", true);
    public static final ModConfigSpec.BooleanValue BLINK = B
            .comment("Piscar sozinho de tempos em tempos.")
            .define("blink", true);
    public static final ModConfigSpec.DoubleValue BLINK_MIN = B
            .comment("Menor intervalo entre duas piscadas, em segundos.")
            .defineInRange("blinkMinSeconds", 2.0, 0.5, 30.0);
    public static final ModConfigSpec.DoubleValue BLINK_MAX = B
            .comment("Maior intervalo entre duas piscadas, em segundos.")
            .defineInRange("blinkMaxSeconds", 6.0, 0.5, 60.0);
    public static final ModConfigSpec.BooleanValue GAZE = B
            .comment("Os olhos acompanham quem está perto e de vez em quando olham para o lado.",
                    "Só funciona em skins cujo olho tem branco e íris lado a lado.")
            .define("gaze", true);
    public static final ModConfigSpec.BooleanValue HURT = B
            .comment("Fecha os olhos com força ao levar dano.")
            .define("hurt", true);
    public static final ModConfigSpec.BooleanValue SLEEP = B
            .comment("Olhos fechados dormindo na cama.")
            .define("sleep", true);
    public static final ModConfigSpec.BooleanValue TIRED = B
            .comment("Com 3 corações ou menos, olhos meio fechados e piscando mais (meio fechado só em olho de 2 linhas).")
            .define("tired", true);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {
    }

    public static ExpressionController.Settings settings() {
        return new ExpressionController.Settings(BLINK.get(), BLINK_MIN.get(), BLINK_MAX.get(), GAZE.get(),
                HURT.get(), SLEEP.get(), TIRED.get());
    }
}
