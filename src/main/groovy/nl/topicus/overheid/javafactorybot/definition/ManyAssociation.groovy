package nl.topicus.overheid.javafactorybot.definition

import com.github.javafaker.Faker
import nl.topicus.overheid.javafactorybot.BaseFactory
import nl.topicus.overheid.javafactorybot.Evaluator

/**
 * Attribute used to define an association with a list of objects, using a factory.
 * A combination of the default overrides, default object, traits and user specified overrides is used to create the
 * associated object using the factory.
 * @param < T >   The type of the associated object.
 */
class ManyAssociation<T> extends AbstractFactoryAttribute<T> implements Attribute {
    Closure<Map<String, Object>> generalOverridesProvider
    List<T> overrides
    int amount
    List<String> traits
    boolean afterBuild = false
    Closure transform = null

    /**
     * Create a new Association which combines user specified overrides with optional default overrides and traits.
     * @param factory The factory to use for the associated object.
     * @param defaultOverrides Default overrides to pass to the factory. Can be overriden by user specified overrides.
     * @param traits List of traits to apply to the associated object.
     */
    ManyAssociation(BaseFactory<T, ? extends Faker> factory) {
        super(factory)
    }

    /**
     * Create a new Association which combines user specified overrides with optional default overrides and traits.
     * @param factoryClass The class of the factory to use for the associated object.
     * The factory itself is lazily initialized using {@link nl.topicus.overheid.javafactorybot.FactoryManager#getFactoryInstance(java.lang.Class)}.
     * @param defaultOverrides Default overrides to pass to the factory. Can be overriden by user specified overrides.
     * @param traits List of traits to apply to the associated object.
     */
    ManyAssociation(Class<? extends BaseFactory<T, ? extends Faker>> factoryClass) {
        super(factoryClass)
    }

    @Override
    def evaluate(Evaluator evaluator, Object owner) {
        def result

        if (overrides != null) {
            result = getFactory().buildList(compileListOverride(overrides, owner))
        } else if (generalOverridesProvider != null) {
            result = getFactory().buildList(amount, generalOverridesProvider(owner))
        } else {
            result = getFactory().buildList(amount)
        }

        transform ? transform(result) : result
    }

    @Override
    def evaluate(Object override, Evaluator evaluator, Object owner) {
        def result

        if (override instanceof List) {
            result = getFactory().buildList(compileListOverride(override, owner))
        } else if (override instanceof Integer) {
            // Build the given amount of object
            result = getFactory().buildList(override)
        } else {
            throw new IllegalArgumentException("Override for a toMany association should be an integer (amount) " +
                    "or a list containing individual overrides/objects. " +
                    "Instead, an instance of type ${override.getClass().name} was received.")
        }

        transform ? transform(result) : result
    }

    List<Object> compileListOverride(List override, Object owner) {
        // A list is given. Each element in the list should be either a map with overrides, or an object (or null)
        // If it is a map, we merge it with the default overrides, just as we do with single associations
        override.collect { it instanceof Map && generalOverridesProvider ? generalOverridesProvider(owner) + it : it }
    }
}
