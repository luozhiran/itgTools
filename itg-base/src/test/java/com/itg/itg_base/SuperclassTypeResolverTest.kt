package com.itg.itg_base

import org.junit.Assert.assertEquals
import org.junit.Test

class SuperclassTypeResolverTest {

    @Test
    fun resolvesDirectConcreteArguments() {
        assertEquals(
            String::class.java,
            resolveSuperclassTypeArgument(Direct::class.java, Root::class.java, 0),
        )
        assertEquals(
            Long::class.javaObjectType,
            resolveSuperclassTypeArgument(Direct::class.java, Root::class.java, 1),
        )
    }

    @Test
    fun resolvesArgumentsAcrossNonGenericAndGenericIntermediateClasses() {
        assertEquals(
            String::class.java,
            resolveSuperclassTypeArgument(Leaf::class.java, Root::class.java, 0),
        )
        assertEquals(
            Long::class.javaObjectType,
            resolveSuperclassTypeArgument(Leaf::class.java, Root::class.java, 1),
        )
    }

    @Test
    fun resolvesTypeVariableBoundByConcreteLeaf() {
        assertEquals(
            String::class.java,
            resolveSuperclassTypeArgument(ConcreteGenericLeaf::class.java, Root::class.java, 0),
        )
        assertEquals(
            Int::class.javaObjectType,
            resolveSuperclassTypeArgument(ConcreteGenericLeaf::class.java, Root::class.java, 1),
        )
    }

    private open class Root<A : Any, B : Any>

    private class Direct : Root<String, Long>()

    private open class GenericMiddle<A : Any, B : Any> : Root<A, B>()
    private open class ConcreteMiddle : GenericMiddle<String, Long>()
    private class Leaf : ConcreteMiddle()

    private open class PartiallyFixedMiddle<T : Any> : Root<String, T>()
    private class ConcreteGenericLeaf : PartiallyFixedMiddle<Int>()
}
