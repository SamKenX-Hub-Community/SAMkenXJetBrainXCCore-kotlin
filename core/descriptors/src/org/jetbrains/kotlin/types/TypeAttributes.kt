/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.types.model.AnnotationMarker
import org.jetbrains.kotlin.util.AttributeArrayOwner
import org.jetbrains.kotlin.util.TypeRegistry
import org.jetbrains.kotlin.utils.addIfNotNull
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass

abstract class TypeAttribute<T : TypeAttribute<T>> : AnnotationMarker {
    abstract fun union(other: @UnsafeVariance T?): T?
    abstract fun intersect(other: @UnsafeVariance T?): T?

    /*
     * This function is used to decide how multiple attributes should be united in presence of typealiases:
     * typealias B = @SomeAttribute(1) A
     * typealias C = @SomeAttribute(2) B
     *
     * For determining attribute value of expanded type of C we should add @SomeAttribute(2) to @SomeAttribute(1)
     *
     * This function must be symmetrical: a.add(b) == b.add(a)
     */
    abstract fun add(other: @UnsafeVariance T?): T
    abstract fun isSubtypeOf(other: @UnsafeVariance T?): Boolean

    abstract val key: KClass<out T>
}

class TypeAttributes private constructor(attributes: List<TypeAttribute<*>>) : AttributeArrayOwner<TypeAttribute<*>, TypeAttribute<*>>(),
    Iterable<TypeAttribute<*>> {

    companion object : TypeRegistry<TypeAttribute<*>, TypeAttribute<*>>() {
        inline fun <reified T : TypeAttribute<T>> attributeAccessor(): ReadOnlyProperty<TypeAttributes, T?> {
            @Suppress("UNCHECKED_CAST")
            return generateNullableAccessor<TypeAttribute<*>, T>(T::class) as ReadOnlyProperty<TypeAttributes, T?>
        }

        val Empty: TypeAttributes = TypeAttributes(emptyList())

        private fun TypeAttribute<*>.predefined(): Pair<TypeAttribute<*>, TypeAttributes> = this to TypeAttributes(this)

        fun create(attributes: List<TypeAttribute<*>>): TypeAttributes {
            return if (attributes.isEmpty()) {
                Empty
            } else {
                TypeAttributes(attributes)
            }
        }
    }

    private constructor(attribute: TypeAttribute<*>) : this(listOf(attribute))

    init {
        for (attribute in attributes) {
            registerComponent(attribute.key, attribute)
        }
    }

    fun union(other: TypeAttributes): TypeAttributes {
        return perform(other) { this.union(it) }
    }

    fun intersect(other: TypeAttributes): TypeAttributes {
        return perform(other) { this.intersect(it) }
    }

    fun add(other: TypeAttributes): TypeAttributes {
        return perform(other) { this.add(it) }
    }

    operator fun contains(attribute: TypeAttribute<*>): Boolean {
        val index = getId(attribute.key)
        return arrayMap[index] != null
    }

    @OptIn(ExperimentalStdlibApi::class)
    operator fun plus(attribute: TypeAttribute<*>): TypeAttributes {
        if (attribute in this) return this
        if (isEmpty()) return TypeAttributes(attribute)
        val newAttributes = buildList {
            addAll(this)
            add(attribute)
        }
        return TypeAttributes(newAttributes)
    }

    fun remove(attribute: TypeAttribute<*>): TypeAttributes {
        if (isEmpty()) return this
        val attributes = arrayMap.filter { it != attribute }
        if (attributes.size == arrayMap.size) return this
        return create(attributes)
    }

    private inline fun perform(other: TypeAttributes, op: TypeAttribute<*>.(TypeAttribute<*>?) -> TypeAttribute<*>?): TypeAttributes {
        if (this.isEmpty() && other.isEmpty()) return this
        val attributes = mutableListOf<TypeAttribute<*>>()
        for (index in indices) {
            val a = arrayMap[index]
            val b = other.arrayMap[index]
            val res = if (a == null) b?.op(a) else a.op(b)
            attributes.addIfNotNull(res)
        }
        return create(attributes)
    }

    override val typeRegistry: TypeRegistry<TypeAttribute<*>, TypeAttribute<*>>
        get() = Companion
}

fun TypeAttributes.toDefaultAnnotations(): Annotations =
    DefaultTypeAttributeTranslator.toAnnotations(this)

fun Annotations.toDefaultAttributes(): TypeAttributes = DefaultTypeAttributeTranslator.toAttributes(this)

fun TypeAttributes.replaceAnnotations(newAnnotations: Annotations): TypeAttributes {
    val withoutCustom = (custom?.let { this.remove(it) } ?: this)
    return withoutCustom.add(newAnnotations.toDefaultAttributes())
}
