package com.sigmundgranaas.forgero.core.state;

import com.sigmundgranaas.forgero.core.condition.ConditionContainer;
import com.sigmundgranaas.forgero.core.condition.Conditional;
import com.sigmundgranaas.forgero.core.property.Property;
import com.sigmundgranaas.forgero.core.property.PropertyContainer;
import com.sigmundgranaas.forgero.core.state.customvalue.CustomValue;
import com.sigmundgranaas.forgero.core.type.Type;
import com.sigmundgranaas.forgero.core.util.match.Context;
import com.sigmundgranaas.forgero.core.util.match.Matchable;
import com.sigmundgranaas.forgero.core.util.match.NameMatch;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ConditionedState implements State, Conditional<ConditionedState> {
    private final IdentifiableContainer id;
    private final ConditionContainer conditions;

    private final List<Property> properties;

    private final Map<String, CustomValue> customData;

    public ConditionedState(IdentifiableContainer id, ConditionContainer conditions, List<Property> properties, Map<String, CustomValue> customData) {
        this.id = id;
        this.conditions = conditions;
        this.properties = properties;
        this.customData = customData;
    }

    public static ConditionedState of(State state) {
        IdentifiableContainer id = new IdentifiableContainer(state.name(), state.nameSpace(), state.type());
        return new ConditionedState(id, Conditional.EMPTY, state.getRootProperties(), state.customValues());
    }

    @Override
    public List<PropertyContainer> conditions() {
        return conditions.conditions();
    }

    @Override
    public ConditionedState applyCondition(PropertyContainer container) {
        return new ConditionedState(id, conditions.applyCondition(container), properties, customData);
    }

    @Override
    public ConditionedState removeCondition(String identifier) {
        return new ConditionedState(id, conditions.removeCondition(identifier), properties, customData);
    }


    @Override
    public String identifier() {
        return id.identifier();
    }

    @Override
    public @NotNull List<Property> getRootProperties() {
        return Stream.of(properties.stream().toList(), conditions().stream().map(PropertyContainer::getRootProperties).flatMap(List::stream).toList()).flatMap(List::stream).toList();
    }

    @Override
    public String name() {
        return id.name();
    }

    @Override
    public String nameSpace() {
        return id.nameSpace();
    }

    @Override
    public Type type() {
        return id.type();
    }

    @Override
    public boolean test(Matchable match, Context context) {
        if (match instanceof NameMatch matcher) {
            return matcher.name().equals(id.name());
        }
        return id.type().test(match, context);
    }

    @Override
    public Map<String, CustomValue> customValues() {
        return this.customData;
    }
}
