package org.jruby.truffle.nodes.rubinius;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.ConditionProfile;
import org.jruby.Ruby;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.core.RubyBasicObject;
import org.jruby.truffle.runtime.core.RubyBignum;

import java.math.BigInteger;
import java.util.Random;

/**
 * Rubinius primitives associated with the Ruby {@code Random} class.
 */

public abstract class RandomPrimitiveNodes {

    @RubiniusPrimitive(name = "randomizer_seed")
    public static abstract class RandomizerSeedPrimitiveNodes extends RubiniusPrimitiveNode {

        private final ConditionProfile nullByteProfile = ConditionProfile.createBinaryProfile();

        public RandomizerSeedPrimitiveNodes(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public RandomizerSeedPrimitiveNodes(RandomizerSeedPrimitiveNodes prev) {
            super(prev);
        }

        @Specialization
        public Long randomizerSeed(RubyBasicObject random) {
            return System.currentTimeMillis();
        }

    }

    @RubiniusPrimitive(name = "randomizer_rand_float")
    public static abstract class RandomizerRandFloatPrimitiveNodes extends RubiniusPrimitiveNode {

        private final ConditionProfile nullByteProfile = ConditionProfile.createBinaryProfile();

        public RandomizerRandFloatPrimitiveNodes(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public RandomizerRandFloatPrimitiveNodes(RandomizerRandFloatPrimitiveNodes prev) {
            super(prev);
        }

        @Specialization
        public Double randomizerRandFloat(RubyBasicObject random) {
            return Math.random();
        }

    }

    @RubiniusPrimitive(name = "randomizer_rand_int")
    public static abstract class RandomizerRandIntPrimitiveNodes extends RubiniusPrimitiveNode {

        private final ConditionProfile nullByteProfile = ConditionProfile.createBinaryProfile();

        public RandomizerRandIntPrimitiveNodes(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public RandomizerRandIntPrimitiveNodes(RandomizerRandIntPrimitiveNodes prev) {
            super(prev);
        }

        @Specialization
        public Long randomizerRandInt(RubyBasicObject random) {
            return System.currentTimeMillis();
        }

    }


    @RubiniusPrimitive(name = "randomizer_gen_seed")
    public static abstract class RandomizerGenSeedPrimitiveNodes extends RubiniusPrimitiveNode {

        private final ConditionProfile nullByteProfile = ConditionProfile.createBinaryProfile();

        public RandomizerGenSeedPrimitiveNodes(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public RandomizerGenSeedPrimitiveNodes(RandomizerGenSeedPrimitiveNodes prev) {
            super(prev);
        }

        @Specialization
        public RubyBignum randomizerGenSeed(RubyBasicObject random) {

            BigInteger randomInt = RandomPrimitiveHelper.randomSeed(getContext().getRuntime().getCurrentContext().runtime);

            return new RubyBignum(getContext().getCoreLibrary().getBignumClass(), randomInt);
        }
    }

    static class RandomPrimitiveHelper {
        private static final int DEFAULT_SEED_CNT = 4;

        @CompilerDirectives.TruffleBoundary
        public static BigInteger randomSeed(Ruby runtime) {
            byte[] seed = new byte[DEFAULT_SEED_CNT * 4];
            runtime.getRandom().nextBytes(seed);
            return (new BigInteger(seed)).abs();
        }
    }
}
