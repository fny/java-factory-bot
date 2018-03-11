package nl.topicus.overheid.javafactorybot

import com.github.javafaker.Faker
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import nl.topicus.overheid.javafactorybot.definition.Association
import nl.topicus.overheid.javafactorybot.definition.Attribute
import nl.topicus.overheid.javafactorybot.definition.ManyAssociation
import nl.topicus.overheid.javafactorybot.definition.ValueAttribute

import java.lang.reflect.ParameterizedType

/**
 * A factory is a special class which is able to generate new valid models, for testing purposes.
 * These models can be randomized by using a faker.
 *
 * @param < M >
 */
abstract class Factory<M> {
    /**
     * A Faker instance which can be used to generate random model properties.
     *
     * @link https://github.com/DiUS/java-faker
     */
    protected final Faker faker = new Faker()

    /**
     * Returns the type of the object which is created by this factory.
     * @return The type of the object which is created by this factory.
     */
    Class<M> getObjectType() {
        // Reflection magic to determine the type of the model
        // Based on https://stackoverflow.com/questions/1901164/get-type-of-a-generic-parameter-in-java-with-reflection
        try {
            ((ParameterizedType) this.class.genericSuperclass).actualTypeArguments[0] as Class<M>
        } catch (ClassCastException e) {
            // This exception explains better what you did wrong
            throw new IllegalStateException("Object type of ${this.class.simpleName} could not be determined. Did you forget to add a type parameter to the factory?", e)
        }
    }

    /**
     * Map containing {@link Attribute}s of this factory. An {@link Attribute} is a definition of an attribute in the
     * (generated) object, and minimally implements a function which, given an instance of {@link Evaluator}, yields the
     * value this attribute should have in the generated object.
     * @return A map containing the {@link Attribute}s of this factory.
     */
    Map<String, Attribute> getAttributes() {
        [:]
    }

    static ValueAttribute attribute(
            @DelegatesTo(Evaluator) @ClosureParams(value = SimpleType, options = "Evaluator") Closure defaultValueGenerator) {
        new ValueAttribute(defaultValueGenerator)
    }

    static <T> Association<T> hasOne(Factory<T> factory) {
        new Association<T>(factory)
    }

    static <T> Association<T> hasOne(Factory<T> factory, T defaultObject) {
        new Association<T>(factory, defaultObject)
    }

    static <T> Association<T> hasOne(Factory<T> factory, Map<String, Object> overrides, List<String> traits = null) {
        new Association<T>(factory, overrides, traits)
    }

    // Special version for groovy syntax
    static <T> Association<T> hasOne(Map<String, Object> overrides, Factory<T> factory, List<String> traits = null) {
        new Association<T>(factory, overrides, traits)
    }

    static <T> ManyAssociation<T> hasMany(Factory<T> factory, int amount, Map<String, Object> overrides = null, List<String> traits = null) {
        new ManyAssociation<T>(factory, amount, overrides, traits)
    }

    // Special version for groovy syntax
    static <T> ManyAssociation<T> hasMany(Map<String, Object> overrides, Factory<T> factory, int amount, List<String> traits = null) {
        new ManyAssociation<T>(factory, amount, overrides, traits)
    }

    static <T> ManyAssociation<T> hasMany(Factory<T> factory, List<Object> defaultOverrides, List<String> traits = null) {
        new ManyAssociation<T>(factory, defaultOverrides, traits)
    }

    /**
     * Returns the passed object.
     * <p>
     * This method exists so it is possible to completely override a relation by passing your own instance, or null.
     * @param buildParameters The custom instance of the object.
     * @param skipHooks If true, the hooks ({@link #onAfterBuild} & {@link #onBuildComplete}) are skipped.
     * This is intended for nested calls by other build methods.
     * @return The passed object
     */
    M build(M model, boolean skipHooks = false) {
        skipHooks ? model : onBuildComplete(onAfterBuild(model))
    }

    /**
     * Returns a new instance.
     * <p>
     * In normal usage, this method should not be overriden. If you want to change how the model is build, use
     * {@link #onAfterBuild} or {@link #internalBuild}.
     *
     * @param skipHooks If true, the hooks ({@link #onAfterBuild} & {@link #onBuildComplete}) are skipped.
     * This is intended for nested calls by other build methods.
     * @return The new instance.
     */
    M build(boolean skipHooks = false) {
        M model = internalBuild(attributes([:]))
        skipHooks ? model : onBuildComplete(onAfterBuild(model))
    }

    /**
     * Returns a new instance that is not saved.
     * <p>
     * In normal usage, this method should not be overriden. If you want to change how the model is build, use
     * {@link #onAfterBuild} or {@link #internalBuild}.
     *
     * @param buildParameters Additional build parameters to use when building a new model. Build parameters allow to define custom values for attributes and relations..
     * @param skipHooks If true, the hooks ({@link #onAfterBuild} & {@link #onBuildComplete}) are skipped.
     * This is intended for nested calls by other build methods.
     * @return The new instance.
     */
    M build(Map buildParameters, boolean skipHooks = false) {
        M model = internalBuild(attributes(buildParameters))
        skipHooks ? model : onBuildComplete(onAfterBuild(model))
    }

    /**
     * Returns a new instance that is not saved.
     *
     * @param buildParameters Additional build parameters to use when building a new model. Build parameters allow to define custom values for attributes and relations..
     * @param traits A list of traits to apply to new model. A trait is basically a collection of attribute/relation updates, meant to create a model
     *                        representing a certain state. The possible traits are specified in the factory.
     * @return The new instance.
     */
    M build(Map buildParameters, List<String> traits) {
        M model = applyTraits(build(buildParameters, true), traits)
        onBuildComplete(onAfterBuild(model))
    }

    /**
     * Returns a new instance that is not saved.
     *
     * @param traits A list of traits to apply to new model. A trait is basically a collection of attribute/relation updates, meant to create a model
     *                        representing a certain state. The possible traits are specified in the factory.
     * @return The new instance.
     */
    M build(List<String> traits) {
        M model = applyTraits(build(true), traits)
        onBuildComplete(onAfterBuild(model))
    }

    /**
     * Returns a list of new instances that are not saved.
     *
     * @param amount The amount of instances to build.
     * @return A list of new instances.
     */
    List<M> buildList(int amount) {
        return buildList(amount, null)
    }

    /**
     * Returns a list of new instances that are not saved.
     *
     * @param amount The amount of instances to build.
     * @param buildParameters Additional build parameters to use when building a new model.
     * @return As list of new instances.
     */
    List<M> buildList(int amount, Map buildParameters) {
        List<M> result = new ArrayList<>(amount)

        amount.times { result.add(buildParameters == null ? build() : build(buildParameters)) }

        result
    }

    /**
     * Returns a list of new instances that are not saved.
     *
     * @param buildParameters A list of additional build parameters. Each element in the list is applied to {@link #build}. So the size of the result is the same as the amount of elements in this list.
     * @return As list of new instances.
     */
    List<M> buildList(List buildParameters) {
        buildParameters.collect({ (M) this.build(it) })
    }

    /**
     * Actual builder of the model, which creates the instance using the computed attributes.
     * This method is not used when {@link #build(M)} is called.
     *
     * @param attributes The computed attributes of the model
     * @return A model with the values from the given attributes
     */
    protected M internalBuild(Map attributes) {
        getObjectType().newInstance(attributes)
    }

    /**
     * Callback which is called after the model is build using the calculated attributes, but before it is returned as result of a build()
     * method. This allows to tweak the model, for example to fix relationships.
     *
     * @param model The model after it was created using the calculated attributes. Can be null
     * @return The model with possible alterations
     */
    M onAfterBuild(M model) {
        model
    }

    /**
     * Callback which is called after the model is completely build, just before it is returned by the build() method.
     * By default, this methods calls {@link FactoryContext#onBuildComplete(Object)} when a context is specified
     * by the {@link ModelFactoryManager}
     *
     * @param model The built model. Can be null
     * @return The built model.
     */
    M onBuildComplete(M model) {
        def context = ModelFactoryManager.instance.currentContext
        if (context != null) {
            context.onBuildComplete(model)
        }
        model
    }

    /**
     * Returns a map of attributes and relations based on the specified default attribute, default relations and build parameters.
     * @param overrides The build parameters specified to override default attributes and/or relations.
     * @return A map of attributes which can be used to create a new instance.
     */
    Map attributes(Map<String, Object> overrides) {
        Evaluator evaluator = new Evaluator(this, overrides)
        evaluator.attributes()
    }

    /**
     * Returns a map containing possible traits for this factory. A trait is a collection of attributes and relations, meant to put the generated model in a certain
     * state. A trait is identified by a name, while the trait itself is declared as a Function over the generated model. When applied, the trait returns the model in the
     * new state.
     *
     * @return A map from trait name to trait function of possible traits for this factory.
     * @see <a href="http://www.rubydoc.info/gems/factory_girl/file/GETTING_STARTED.md#Traits">Traits applied in ruby factories</a>
     */
    protected Map<String, Closure> getTraits() {
        null
    }

    M applyTraits(M object, List<String> traits) {
        Map<String, Closure> availableTraits = getTraits()
        if (availableTraits != null) {
            traits.forEach { String traitName -> availableTraits.get(traitName)(object) }
        }
        object
    }

    /**
     * Returns a closure which takes buildParameters and build a list of objects depending on these parameters.
     *
     * <ul>
     *     <li>If a list is provided, this list is used as a base for the list of objects (each item applied to #build)</li>
     *     <li>If a map is provided, a number of objects will be build, each with the given map as buildparameters.
     *     The amount of objects equals the default amount of objects. </li>
     * </ul>
     * This method is intended for toMany relations in {@link #getDefaultRelations()}.
     *
     * Example:
     * <pre>
     * class UserFactory extends Factory
     *     defaultRelations = [addresses: useOrBuildList(AdresFactory.instance, 3)]
     *     ...
     * }
     * </pre>
     *
     * <ul>
     * <li>When calling with <code>userFactory.build()</code> a user with 3 addresses is generated.</li>
     * <li>When calling with <code>userFactory.build([addresses: [street: 'foo']])</code> a user with 3 addresses is generated where each address has the value 'foo' for the street property</li>
     * <li>When calling with <code>userFactory.build([addresses: [])</code> a user with 0 addresses is generated.</li>
     * <li>When calling with <code>userFactory.build([addresses: [[:], [:]])</code> a user with 2 addresses is generated.</li>
     * <li>When calling with <code>userFactory.build([addresses: 2)</code> a user with 2 addresses is generated.
     * This is the equal to calling <code>userFactory.build([addresses: [[:], [:]])</code>.</li>
     * </ul>
     *
     * @param factory The factory to use for building the objects
     * @param defaultAmount The default amount of objects to create.
     * @return A list of objects created with the factory.
     */
    static Closure<List> useOrBuildList(Factory factory, int defaultAmount) {
        return { Object args ->
            if (args instanceof List) {
                // For each element in the list, build a new model
                return factory.buildList(args)
            } else if (args instanceof Integer) {
                // Build the given amount of models
                return factory.buildList(args)
            } else {
                // Use arguments to build the default amount of models
                return factory.buildList(defaultAmount, args as Map)
            }
        }
    }

    /**
     * Returns a closure which takes buildParameters and build a list of objects depending on these parameters.
     * <ul>
     *     <li>If a list is provided, this list is used as a base for the list of objects (each item applied to #build)</li>
     *     <li>If a map is provided, this map is merged with each item in the default build parameters and used to build an object.
     *     So, the amount of objects equals the size of the default list</li>
     * </ul>
     *
     * This method is intended for toMany relations in {@link #getDefaultRelations()}.
     *
     * Example:
     * <pre>
     * class UserFactory extends Factory
     *    defaultRelations = [addresses: useOrBuildList(AdresFactory.instance, [[street: 'a', number: 10], [street: 'b']])]
     *    ...
     * }
     * </pre>
     *
     * <ul>
     * <li>When calling with <code>userFactory.build()</code> a user with 2 addresses is generated with the given streets. The second address has a random number (given AdresFactory has a default value for this).</li>
     * <li>When calling with <code>userFactory.build([addresses: [street: 'foo']])</code> a user with 2 addresses is generated where each address has the value 'foo' for the street property. The first address still has number 10.</li>
     * <li>When calling with <code>userFactory.build([addresses: [])</code> a user with 0 addresses is generated.</li>
     * <li>When calling with <code>userFactory.build([addresses: [[:], [:], [:]])</code> a user with 3 addresses is generated.</li>
     * <li>When calling with <code>userFactory.build([addresses: 3])</code> a user with 3 addresses is generated.
     * This is equal to calling <code>userFactory.build([addresses: [[:], [:], [:]])</code></li>
     * </ul>
     *
     * @param factory The factory to use for building the objects
     * @param defaultAmount The default amount of objects to create.
     * @return A list of objects created with the factory.
     */
    static Closure<List> useOrBuildList(Factory factory, List defaultBuildParameters) {
        return { Object args ->
            if (args instanceof List) {
                // A list of build parameters: use each list element to build a new object
                factory.buildList(args)
            } else if (args instanceof Integer) {
                // Build the given amount of models
                return factory.buildList(args)
            } else if (args instanceof Map && !(args as Map).isEmpty()) {
                // A map containing buildParameters: merge with each list item in the default parameters and use these to build objects
                factory.buildList(defaultBuildParameters.collect { it + args })
            } else {
                // No build parameters: build a list of objects with the default buildParameters
                factory.buildList(defaultBuildParameters)
            }
        }
    }
}
//
//class SimpleFactory {
//    def methodMissing(String name, args) {
//        println(name)
//    }
//
//    /**
//     * A Faker instance which can be used to generate random model properties.
//     *
//     * @link https://github.com/DiUS/java-faker
//     */
//    protected final Faker faker = new Faker()
//
//    def attr(String name, Closure defaultValueProvider = {}) {
//        attribute(name, defaultValueProvider)
//    }
//
//    def attribute(String name, Closure defaultValueProvider = {}) {
//        println("attribute $name")
//    }
//
//    def <T> void association(String name, Class<T> type) {
//        println("association $name")
//    }
//
//    // Test methode om meer als factory-bot te doen
//    static <T> void factory(Class<T> type, @DelegatesTo(SimpleFactory) Closure block) {
//        def factory = new SimpleFactory()
//        factory.with block
//    }
//}