package com.itg.itg_base

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

/** 解析多层继承链中目标泛型父类的具体类型参数。 */
internal fun resolveSuperclassTypeArgument(
    sourceClass: Class<*>,
    targetClass: Class<*>,
    index: Int,
): Class<*> {
    val typeMappings = mutableMapOf<TypeVariable<*>, Type>()
    var currentClass = sourceClass

    while (currentClass != Any::class.java) {
        val genericSuper = currentClass.genericSuperclass
            ?: break
        when (genericSuper) {
            is ParameterizedType -> {
                val rawClass = genericSuper.rawType as? Class<*>
                    ?: error("${currentClass.name} 的父类 ${genericSuper.rawType} 不是具体 Class。")
                val resolvedArguments = genericSuper.actualTypeArguments.map { argument ->
                    resolveMappedType(argument, typeMappings)
                }
                rawClass.typeParameters.zip(resolvedArguments).forEach { (variable, argument) ->
                    typeMappings[variable] = argument
                }
                if (rawClass == targetClass) {
                    check(index in resolvedArguments.indices) {
                        "${targetClass.simpleName} 缺少第 ${index + 1} 个泛型参数。"
                    }
                    return resolvedArguments[index].toConcreteClass(
                        sourceClass = sourceClass,
                        targetClass = targetClass,
                        index = index,
                        mappings = typeMappings,
                    )
                }
                currentClass = rawClass
            }

            is Class<*> -> {
                if (genericSuper == targetClass) {
                    error(
                        "${sourceClass.simpleName} 继承 ${targetClass.simpleName} 时丢失了泛型信息；" +
                            "请在继承链中保留具体 ViewBinding 和 ViewModel 类型。"
                    )
                }
                currentClass = genericSuper
            }

            else -> error("无法解析 ${currentClass.name} 的父类类型 $genericSuper。")
        }
    }

    error("${sourceClass.name} 不是 ${targetClass.name} 的有效子类。")
}

private fun resolveMappedType(
    type: Type,
    mappings: Map<TypeVariable<*>, Type>,
): Type {
    var resolved = type
    val visited = mutableSetOf<TypeVariable<*>>()
    while (resolved is TypeVariable<*> && visited.add(resolved)) {
        resolved = mappings[resolved] ?: break
    }
    return resolved
}

private fun Type.toConcreteClass(
    sourceClass: Class<*>,
    targetClass: Class<*>,
    index: Int,
    mappings: Map<TypeVariable<*>, Type>,
): Class<*> {
    val resolved = resolveMappedType(this, mappings)
    return when (resolved) {
        is Class<*> -> resolved
        is ParameterizedType -> resolved.rawType as? Class<*>
            ?: unresolvedType(sourceClass, targetClass, index, resolved)
        is WildcardType -> resolved.upperBounds.firstOrNull()
            ?.toConcreteClass(sourceClass, targetClass, index, mappings)
            ?: unresolvedType(sourceClass, targetClass, index, resolved)
        else -> unresolvedType(sourceClass, targetClass, index, resolved)
    }
}

private fun unresolvedType(
    sourceClass: Class<*>,
    targetClass: Class<*>,
    index: Int,
    type: Type,
): Nothing = error(
    "${sourceClass.simpleName} 无法解析 ${targetClass.simpleName} 的第 ${index + 1} 个泛型参数 $type；" +
        "请确保最终子类的继承链提供具体类型。"
)
