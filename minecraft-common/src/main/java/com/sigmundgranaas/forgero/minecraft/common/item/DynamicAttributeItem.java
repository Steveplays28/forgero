package com.sigmundgranaas.forgero.minecraft.common.item;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.sigmundgranaas.forgero.core.property.PropertyContainer;
import com.sigmundgranaas.forgero.core.property.Target;
import com.sigmundgranaas.forgero.core.property.v2.Attribute;
import com.sigmundgranaas.forgero.core.property.v2.attribute.attributes.*;
import com.sigmundgranaas.forgero.core.state.State;
import com.sigmundgranaas.forgero.minecraft.common.conversion.StateConverter;
import com.sigmundgranaas.forgero.minecraft.common.mixins.ItemUUIDMixin;
import com.sigmundgranaas.forgero.minecraft.common.toolhandler.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public interface DynamicAttributeItem extends DynamicAttributeTool, DynamicDurability, DynamicEffectiveNess, DynamicMiningLevel, DynamicMiningSpeed {
    LoadingCache<ItemStack, ImmutableMultimap<EntityAttribute, EntityAttributeModifier>> multiMapCache = CacheBuilder.newBuilder()
            .maximumSize(600)
            .expireAfterAccess(Duration.of(1, ChronoUnit.MINUTES))
            .build(new CacheLoader<>() {
                @Override
                public @NotNull ImmutableMultimap<EntityAttribute, EntityAttributeModifier> load(@NotNull ItemStack stack) {
                    return ImmutableMultimap.<EntityAttribute, EntityAttributeModifier>builder().build();
                }
            });

    LoadingCache<ItemStack, Float> miningSpeedCache = CacheBuilder.newBuilder()
            .maximumSize(600)
            .expireAfterAccess(Duration.of(1, ChronoUnit.MINUTES))
            .build(new CacheLoader<>() {
                @Override
                public @NotNull Float load(@NotNull ItemStack stack) {
                    return 1f;
                }
            });

    UUID TEST_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A34DB5CF");
    UUID ADDITION_ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("CB3F55D5-655C-4F38-A497-9C13A33DB5CF");

    UUID ADDITION_HEALTH_MODIFIER_ID = UUID.randomUUID();

    UUID ADDITION_LUCK_MODIFIER_ID = UUID.fromString("CC3F55D5-755C-4F38-A497-9C13A33DB5CF");

    UUID ADDITION_ARMOR_MODIFIER_ID = UUID.fromString("AC3F55D5-755C-4F38-A497-9C13A63DB5CF");

    PropertyContainer dynamicProperties(ItemStack stack);

    PropertyContainer defaultProperties();

    default boolean isEquippable() {
        return true;
    }

    @Override
    default int getMiningLevel(ItemStack stack) {
        return MiningLevel.apply(dynamicProperties(stack));
    }

    @Override
    default int getMiningLevel() {
        return MiningLevel.apply(defaultProperties());
    }


    @Override
    default Multimap<EntityAttribute, EntityAttributeModifier> getDynamicModifiers(EquipmentSlot slot, ItemStack stack, @Nullable LivingEntity user) {
        if (slot.equals(EquipmentSlot.MAINHAND) && stack.getItem() instanceof DynamicAttributeItem && isEquippable()) {
            try {
                return multiMapCache.get(stack, () -> createMultiMap(stack));
            } catch (ExecutionException e) {
                return EMPTY;
            }
        } else {
            return EMPTY;
        }
    }

    private ImmutableMultimap<EntityAttribute, EntityAttributeModifier> createMultiMap(ItemStack stack) {
        Target target = Target.createEmptyTarget();
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        float currentToolDamage = AttackDamage.apply(dynamicProperties(stack), target);
        float baseToolDamage = AttackDamage.apply(defaultProperties());
        //Base attack damage
        builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ItemUUIDMixin.getAttackDamageModifierID(), "Tool modifier", baseToolDamage, EntityAttributeModifier.Operation.ADDITION));

        //Attack damage addition
        builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ADDITION_ATTACK_DAMAGE_MODIFIER_ID, "Attack Damage Addition", currentToolDamage - baseToolDamage, EntityAttributeModifier.Operation.ADDITION));

        //Additional armor
        int luck = LuckHandler.of(dynamicProperties(stack)).map(Attribute::asInt).orElse(0);
        builder.put(EntityAttributes.GENERIC_LUCK, new EntityAttributeModifier(ADDITION_LUCK_MODIFIER_ID, "Luck addition", luck, EntityAttributeModifier.Operation.ADDITION));

        //Additional armor
        float armor = Armor.of(dynamicProperties(stack)).asFloat();
        builder.put(EntityAttributes.GENERIC_ARMOR, new EntityAttributeModifier(ADDITION_ARMOR_MODIFIER_ID, "Armor addition", armor, EntityAttributeModifier.Operation.ADDITION));

        //Additional health
        int additionalHealth = AdditionalHealthHandler.of(dynamicProperties(stack)).map(Attribute::asInt).orElse(0);
        builder.put(EntityAttributes.GENERIC_MAX_HEALTH, new EntityAttributeModifier(ADDITION_HEALTH_MODIFIER_ID, "Health addition", additionalHealth, EntityAttributeModifier.Operation.ADDITION));

        //Attack speed
        float baseAttackSpeed = AttackSpeed.apply(dynamicProperties(stack), target);
        float currentAttackSpeed = AttackSpeed.apply(defaultProperties());
        if (currentAttackSpeed != baseAttackSpeed) {
            builder.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(TEST_UUID, "Tool attack speed addition", baseAttackSpeed - currentAttackSpeed, EntityAttributeModifier.Operation.ADDITION));
        }
        return builder.build();
    }

    @Override
    default int getDurability(ItemStack stack) {
        return Durability.apply(dynamicProperties(stack));
    }

    default int getItemBarStep(ItemStack stack) {
        return Math.round(13.0F - (float) stack.getDamage() * 13.0F / (float) getDurability(stack));
    }

    default int getDurabilityColor(ItemStack stack) {
        var durability = (float) getDurability(stack);
        float f = Math.max(0.0F, (durability - (float) stack.getDamage()) / durability);
        return MathHelper.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    @Override
    default float getMiningSpeedMultiplier(BlockState state, ItemStack stack) {
        if (stack.getItem() instanceof State dynamic && isEffectiveOn(state, stack)) {
            try {
                return miningSpeedCache.get(stack, () -> mingSpeedCalculation(StateConverter.of(stack).orElse(dynamic), state));
            } catch (ExecutionException e) {
                return 1f;
            }
        }
        return 1f;
    }

    @Override
    default boolean isEffectiveOn(BlockState state, ItemStack stack) {
        return isEffective(state, stack) && isCorrectMiningLevel(state, getMiningLevel(stack));
    }

    private float mingSpeedCalculation(PropertyContainer dynamic, BlockState state) {
        Target target = new BlockBreakingEfficiencyTarget(state);
        return MiningSpeed.apply(dynamic, target);
    }
}
