package com.itg.itg_ksp.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.itg.itg_ksp.annotations.ItgBind
import com.itg.itg_ksp.annotations.ItgAutoTextField
import com.itg.itg_ksp.annotations.ItgContentsSame
import com.itg.itg_ksp.annotations.ItgPayload
import com.itg.itg_ksp.annotations.ItgTabHost
import com.itg.itg_ksp.annotations.ItgTabItem
import java.io.OutputStreamWriter

private const val VIEW_BINDING_ANNOTATION = "com.itg.itg_ksp.annotations.ItgViewBindingItem"
private const val DATA_BINDING_ANNOTATION = "com.itg.itg_ksp.annotations.ItgDataBindingItem"
private const val BIND_ANNOTATION = "com.itg.itg_ksp.annotations.ItgBind"
private const val PAYLOAD_ANNOTATION = "com.itg.itg_ksp.annotations.ItgPayload"
private const val CONTENTS_SAME_ANNOTATION = "com.itg.itg_ksp.annotations.ItgContentsSame"
private const val AUTO_TEXT_FIELD_ANNOTATION = "com.itg.itg_ksp.annotations.ItgAutoTextField"
private const val TAB_HOST_ANNOTATION = "com.itg.itg_ksp.annotations.ItgTabHost"
private const val TAB_ITEM_ANNOTATION = "com.itg.itg_ksp.annotations.ItgTabItem"

private const val ITEM_LIST_ITEM = "com.itg.itg_ui.recycler.ItgListItem"
private const val FRAGMENT_CLASS = "androidx.fragment.app.Fragment"

private data class ViewBindingItemSpec(
    val itemType: String,
    val bindingType: String,
    val actionsType: String,
    val itemKeyProperty: String,
    val bindFunction: KSFunctionDeclaration?,
    val autoFields: List<AutoTextFieldSpec>,
    val payloadFunction: KSFunctionDeclaration?,
    val contentsSameFunction: KSFunctionDeclaration?,
    val sourceFiles: List<KSFile>,
)

private data class AutoTextFieldSpec(
    val propertyName: String,
    val viewName: String,
    val payloadKey: String,
)

private data class DataBindingItemSpec(
    val itemType: String,
    val actionsType: String,
    val itemKeyProperty: String,
    val layoutRes: String,
    val itemVariableId: String,
    val actionsVariableId: String,
    val payloadFunction: KSFunctionDeclaration?,
    val contentsSameFunction: KSFunctionDeclaration?,
    val sourceFiles: List<KSFile>,
)

private data class TabHostSpec(
    val hostType: String,
    val packageName: String,
    val groupName: String,
    val tabMode: Int,
    val tabGravity: Int,
    val defaultPosition: Int,
    val swipeable: Boolean,
    val offscreenPageLimit: Int,
    val autoTitle: Boolean,
    val lazyLoadOnFirstSelect: Boolean,
    val sourceFiles: List<KSFile>,
)

private data class TabItemSpec(
    val fragmentType: String,
    val packageName: String,
    val groupName: String,
    val title: String,
    val titleResExpression: String,
    val iconResExpression: String,
    val argumentsExpression: String,
    val badgeCount: Int,
    val badgeDot: Boolean,
    val order: Int,
    val sourceFiles: List<KSFile>,
)

class ItgRecyclerProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val logger: KSPLogger = environment.logger
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val viewItems = resolver.getSymbolsWithAnnotation(VIEW_BINDING_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull(::readViewBindingItem)
            .toList()

        val dataItems = resolver.getSymbolsWithAnnotation(DATA_BINDING_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull(::readDataBindingItem)
            .toList()

        val tabHosts = resolver.getSymbolsWithAnnotation(TAB_HOST_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull(::readTabHost)
            .toList()

        val tabItems = resolver.getSymbolsWithAnnotation(TAB_ITEM_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull(::readTabItem)
            .toList()

        val grouped = buildList {
            viewItems.forEach { add(it.actionsType to it) }
            dataItems.forEach { add(it.actionsType to it) }
        }.groupBy({ it.first }, { it.second })

        grouped.forEach { (_, specs) -> generateRegistry(specs) }
        generateTabs(tabHosts, tabItems)
        generated = true
        return emptyList()
    }

    private fun readViewBindingItem(classDeclaration: KSClassDeclaration): ViewBindingItemSpec? {
        if (classDeclaration.classKind != ClassKind.CLASS) {
            logger.error("@ItgViewBindingItem can only be used on regular classes.", classDeclaration)
            return null
        }
        val annotation = classDeclaration.findAnnotation(VIEW_BINDING_ANNOTATION) ?: return null
        val itemKeyProperty = annotation.stringValue("itemKeyProperty")
        if (!validateItemIdentity(classDeclaration, itemKeyProperty)) return null

        val bindingType = annotation.stringValue("bindingClassName")
        val actionsType = annotation.stringValue("actionsClassName")
        val declaredFunctions = classDeclaration.declarations.filterIsInstance<KSFunctionDeclaration>()
        val bindFunction = declaredFunctions.firstOrNull {
            it.annotations.hasAnnotation(BIND_ANNOTATION)
        }
        val autoFields = declaredProperties(classDeclaration)
            .mapNotNull { readAutoTextField(it) }
            .toList()
        if (bindFunction == null && autoFields.isEmpty()) {
            logger.error(
                "Class ${classDeclaration.qualifiedName?.asString()} must declare @ItgBind or @ItgAutoTextField fields.",
                classDeclaration,
            )
            return null
        }
        bindFunction?.let { validateBindFunction(classDeclaration, it, bindingType, actionsType) }

        return ViewBindingItemSpec(
            itemType = classDeclaration.qualifiedName!!.asString(),
            bindingType = bindingType,
            actionsType = actionsType,
            itemKeyProperty = itemKeyProperty,
            bindFunction = bindFunction,
            autoFields = autoFields,
            payloadFunction = declaredFunctions.firstOrNull {
                it.annotations.hasAnnotation(PAYLOAD_ANNOTATION)
            },
            contentsSameFunction = declaredFunctions.firstOrNull {
                it.annotations.hasAnnotation(CONTENTS_SAME_ANNOTATION)
            },
            sourceFiles = classDeclaration.containingFile?.let(::listOf).orEmpty(),
        )
    }

    private fun readDataBindingItem(classDeclaration: KSClassDeclaration): DataBindingItemSpec? {
        if (classDeclaration.classKind != ClassKind.CLASS) {
            logger.error("@ItgDataBindingItem can only be used on regular classes.", classDeclaration)
            return null
        }
        val annotation = classDeclaration.findAnnotation(DATA_BINDING_ANNOTATION) ?: return null
        val itemKeyProperty = annotation.stringValue("itemKeyProperty")
        if (!validateItemIdentity(classDeclaration, itemKeyProperty)) return null

        val declaredFunctions = classDeclaration.declarations.filterIsInstance<KSFunctionDeclaration>()

        return DataBindingItemSpec(
            itemType = classDeclaration.qualifiedName!!.asString(),
            actionsType = annotation.stringValue("actionsClassName"),
            itemKeyProperty = itemKeyProperty,
            layoutRes = annotation.stringValue("layoutExpression"),
            itemVariableId = annotation.stringValue("itemVariableExpression"),
            actionsVariableId = annotation.stringValue("actionsVariableExpression"),
            payloadFunction = declaredFunctions.firstOrNull {
                it.annotations.hasAnnotation(PAYLOAD_ANNOTATION)
            },
            contentsSameFunction = declaredFunctions.firstOrNull {
                it.annotations.hasAnnotation(CONTENTS_SAME_ANNOTATION)
            },
            sourceFiles = classDeclaration.containingFile?.let(::listOf).orEmpty(),
        )
    }

    private fun readTabHost(classDeclaration: KSClassDeclaration): TabHostSpec? {
        if (classDeclaration.classKind != ClassKind.CLASS) {
            logger.error("@ItgTabHost can only be used on regular classes.", classDeclaration)
            return null
        }
        val annotation = classDeclaration.findAnnotation(TAB_HOST_ANNOTATION) ?: return null

        return TabHostSpec(
            hostType = classDeclaration.qualifiedName!!.asString(),
            packageName = classDeclaration.packageName.asString(),
            groupName = annotation.stringValue("groupName"),
            tabMode = annotation.intValue("tabMode"),
            tabGravity = annotation.intValue("tabGravity"),
            defaultPosition = annotation.intValue("defaultPosition"),
            swipeable = annotation.booleanValue("swipeable"),
            offscreenPageLimit = annotation.intValue("offscreenPageLimit"),
            autoTitle = annotation.booleanValue("autoTitle"),
            lazyLoadOnFirstSelect = annotation.booleanValue("lazyLoadOnFirstSelect"),
            sourceFiles = classDeclaration.containingFile?.let(::listOf).orEmpty(),
        )
    }

    private fun readTabItem(classDeclaration: KSClassDeclaration): TabItemSpec? {
        if (classDeclaration.classKind != ClassKind.CLASS) {
            logger.error("@ItgTabItem can only be used on regular classes.", classDeclaration)
            return null
        }
        if (!isFragmentClass(classDeclaration)) {
            logger.error(
                "Class ${classDeclaration.qualifiedName?.asString()} must extend Fragment.",
                classDeclaration,
            )
            return null
        }
        val annotation = classDeclaration.findAnnotation(TAB_ITEM_ANNOTATION) ?: return null

        return TabItemSpec(
            fragmentType = classDeclaration.qualifiedName!!.asString(),
            packageName = classDeclaration.packageName.asString(),
            groupName = annotation.stringValue("groupName"),
            title = annotation.stringValue("title"),
            titleResExpression = annotation.stringValue("titleResExpression"),
            iconResExpression = annotation.stringValue("iconResExpression"),
            argumentsExpression = annotation.stringValue("argumentsExpression"),
            badgeCount = annotation.intValue("badgeCount"),
            badgeDot = annotation.booleanValue("badgeDot"),
            order = annotation.intValue("order"),
            sourceFiles = classDeclaration.containingFile?.let(::listOf).orEmpty(),
        )
    }

    private fun generateRegistry(specs: List<Any>) {
        if (specs.isEmpty()) return

        val actionsType = when (val first = specs.first()) {
            is ViewBindingItemSpec -> first.actionsType
            is DataBindingItemSpec -> first.actionsType
            else -> error("Unsupported spec type.")
        }
        val actionsPackage = actionsType.substringBeforeLast('.', missingDelimiterValue = "")
        val actionsSimpleName = actionsType.substringAfterLast('.')
        val baseName = actionsSimpleName.removeSuffix("Actions").ifBlank { actionsSimpleName }
        val registryName = "Generated${baseName}Registry"
        val adapterName = "create${baseName}RecyclerAdapter"

        val viewSpecs = specs.filterIsInstance<ViewBindingItemSpec>()
        val dataSpecs = specs.filterIsInstance<DataBindingItemSpec>()
        val sourceFiles = specs.flatMap {
            when (it) {
                is ViewBindingItemSpec -> it.sourceFiles
                is DataBindingItemSpec -> it.sourceFiles
                else -> emptyList()
            }
        }.distinct().toTypedArray()

        val file = codeGenerator.createNewFile(
            Dependencies(aggregating = true, *sourceFiles),
            actionsPackage,
            registryName,
        )

        OutputStreamWriter(file).use { writer ->
            writer.appendLine("package $actionsPackage")
            writer.appendLine()
            writer.appendLine("import com.itg.itg_ksp.runtime.GeneratedRecyclerRegistry")
            writer.appendLine("import com.itg.itg_ksp.runtime.itgGeneratedRecyclerAdapter")
            writer.appendLine("import com.itg.itg_ui.recycler.ItemRendererRegistryBuilder")
            writer.appendLine("import com.itg.itg_ui.recycler.dataBinding")
            writer.appendLine("import com.itg.itg_ui.recycler.viewBinding")
            writer.appendLine("import com.itg.itg_ui.recycler.viewBindingWithPayloads")
            writer.appendLine()
            writer.appendLine("object $registryName : GeneratedRecyclerRegistry<$actionsType> {")
            writer.appendLine("    override fun register(builder: ItemRendererRegistryBuilder<$actionsType>) {")
            viewSpecs.forEach { writer.append(generateViewBindingRegistration(it)) }
            dataSpecs.forEach { writer.append(generateDataBindingRegistration(it)) }
            writer.appendLine("    }")
            writer.appendLine("}")
            writer.appendLine()
            writer.appendLine("fun $adapterName(actions: $actionsType) = itgGeneratedRecyclerAdapter(actions, $registryName)")
        }
    }

    private fun generateViewBindingRegistration(spec: ViewBindingItemSpec): String = buildString {
        val payloadExpression = when {
            spec.payloadFunction != null -> {
                "getChangePayload = { oldItem, newItem -> newItem.${spec.payloadFunction.simpleName.asString()}(oldItem) },"
            }
            spec.autoFields.isNotEmpty() -> {
                val changedItems = spec.autoFields.joinToString(", ") { field ->
                    "if (oldItem.${field.propertyName} != newItem.${field.propertyName}) \"${field.payloadKey}\" else null"
                }
                "getChangePayload = { oldItem, newItem -> listOfNotNull($changedItems).takeIf { it.isNotEmpty() } },"
            }
            else -> null
        }
        val contentsExpression = spec.contentsSameFunction?.let {
            "areContentsTheSame = { oldItem, newItem -> newItem.${it.simpleName.asString()}(oldItem) },"
        }
        val usesPayloads = spec.bindFunction?.parameters?.size == 3 || spec.autoFields.isNotEmpty()
        val rendererCall = if (usesPayloads) {
            "builder.viewBindingWithPayloads<${spec.itemType}, ${spec.bindingType}, ${spec.actionsType}>("
        } else {
            "builder.viewBinding<${spec.itemType}, ${spec.bindingType}, ${spec.actionsType}>("
        }
        appendLine("        $rendererCall")
        appendLine("            inflate = ${spec.bindingType}::inflate,")
        if (spec.itemKeyProperty.isNotBlank()) {
            appendLine("            itemKey = { item -> item.${spec.itemKeyProperty} },")
        }
        payloadExpression?.let { appendLine("            $it") }
        contentsExpression?.let { appendLine("            $it") }
        when {
            spec.bindFunction != null && spec.bindFunction.parameters.size == 3 -> {
                appendLine("        ) { item, actions, payloads ->")
                appendLine("            item.${spec.bindFunction.simpleName.asString()}(this, actions, payloads)")
            }
            spec.bindFunction != null && usesPayloads -> {
                appendLine("        ) { item, actions, _ ->")
                appendLine("            item.${spec.bindFunction.simpleName.asString()}(this, actions)")
            }
            spec.bindFunction != null -> {
                appendLine("        ) { item, actions ->")
                appendLine("            item.${spec.bindFunction.simpleName.asString()}(this, actions)")
            }
            spec.autoFields.isNotEmpty() -> {
                appendLine("        ) { item, actions, _ ->")
                spec.autoFields.forEach { field ->
                    appendLine("            ${fieldBindingExpression(field)}")
                }
            }
        }
        appendLine("        }")
    }

    private fun generateDataBindingRegistration(spec: DataBindingItemSpec): String = buildString {
        val payloadExpression = spec.payloadFunction?.let {
            "getChangePayload = { oldItem, newItem -> newItem.${it.simpleName.asString()}(oldItem) },"
        }
        val contentsExpression = spec.contentsSameFunction?.let {
            "areContentsTheSame = { oldItem, newItem -> newItem.${it.simpleName.asString()}(oldItem) },"
        }
        appendLine("        builder.dataBinding<${spec.itemType}, ${spec.actionsType}>(")
        appendLine("            layoutId = ${spec.layoutRes},")
        appendLine("            itemVariableId = ${spec.itemVariableId},")
        appendLine("            actionsVariableId = ${spec.actionsVariableId},")
        if (spec.itemKeyProperty.isNotBlank()) {
            appendLine("            itemKey = { item -> item.${spec.itemKeyProperty} },")
        }
        payloadExpression?.let { appendLine("            $it") }
        contentsExpression?.let { appendLine("            $it") }
        appendLine("        )")
    }

    private fun generateTabs(hosts: List<TabHostSpec>, items: List<TabItemSpec>) {
        if (hosts.isEmpty() || items.isEmpty()) return

        hosts.forEach { host ->
            val hostItems = items
                .filter { it.groupName == host.groupName }
                .sortedBy { it.order }
            if (hostItems.isEmpty()) return@forEach

            val file = codeGenerator.createNewFile(
                Dependencies(
                    aggregating = true,
                    *(host.sourceFiles + hostItems.flatMap { it.sourceFiles }).distinct().toTypedArray(),
                ),
                host.packageName,
                "Generated${host.groupName}Tab",
            )

            OutputStreamWriter(file).use { writer ->
                writer.appendLine("package ${host.packageName}")
                writer.appendLine()
            writer.appendLine("import com.itg.itg_ui.tab.TabBadge")
                writer.appendLine("import com.itg.itg_ui.tab.TabConfig")
                writer.appendLine("import com.itg.itg_ui.tab.TabItem")
                writer.appendLine("import com.google.android.material.tabs.TabLayout")
                writer.appendLine()
                writer.appendLine("fun create${host.groupName}TabItems(): List<TabItem<*>> = listOf(")
                hostItems.forEach { item ->
                    writer.appendLine("    ${generateTabItemExpression(item)},")
                }
                writer.appendLine(")")
                writer.appendLine()
                writer.appendLine("fun create${host.groupName}TabConfig(): TabConfig = TabConfig(")
                writer.appendLine("    tabMode = ${host.tabMode},")
                writer.appendLine("    tabGravity = ${host.tabGravity},")
                writer.appendLine("    defaultPosition = ${host.defaultPosition},")
                writer.appendLine("    swipeable = ${host.swipeable},")
                writer.appendLine("    offscreenPageLimit = ${host.offscreenPageLimit},")
                writer.appendLine("    autoTitle = ${host.autoTitle},")
                writer.appendLine("    lazyLoadOnFirstSelect = ${host.lazyLoadOnFirstSelect},")
                writer.appendLine(")")
            }
        }
    }

    private fun generateTabItemExpression(item: TabItemSpec): String {
        val parts = mutableListOf<String>()
        if (item.title.isNotBlank()) parts += "title = \"${escapeKotlinString(item.title)}\""
        if (item.titleResExpression.isNotBlank()) parts += "titleRes = ${item.titleResExpression}"
        if (item.iconResExpression.isNotBlank()) parts += "iconRes = ${item.iconResExpression}"
        if (item.argumentsExpression.isNotBlank()) parts += "arguments = ${item.argumentsExpression}"
        if (item.badgeDot || item.badgeCount >= 0) {
            parts += "badge = TabBadge(count = ${item.badgeCount.coerceAtLeast(0)}, showAsDot = ${item.badgeDot})"
        }
        parts += "fragmentClass = ${item.fragmentType}::class.java"
        return "TabItem(${parts.joinToString(", ")})"
    }

    private fun validateBindFunction(
        classDeclaration: KSClassDeclaration,
        bindFunction: KSFunctionDeclaration,
        bindingType: String,
        actionsType: String,
    ) {
        val parameters = bindFunction.parameters
        if (parameters.size != 2 && parameters.size != 3) {
            logger.error(
                "Class ${classDeclaration.qualifiedName?.asString()} @ItgBind function must accept binding/actions or binding/actions/payloads.",
                bindFunction,
            )
            return
        }
        val firstParamType = parameters[0].type.toString()
        val secondParamType = parameters[1].type.toString()
        if (!firstParamType.contains(bindingType.substringAfterLast('.'))) {
            logger.error("@ItgBind first parameter must be $bindingType.", bindFunction)
        }
        if (!secondParamType.contains(actionsType.substringAfterLast('.'))) {
            logger.error("@ItgBind second parameter must be $actionsType.", bindFunction)
        }
    }

    private fun implementsItgListItem(classDeclaration: KSClassDeclaration): Boolean =
        isSubclassOf(classDeclaration, ITEM_LIST_ITEM)

    private fun validateItemIdentity(
        classDeclaration: KSClassDeclaration,
        itemKeyProperty: String,
    ): Boolean {
        if (itemKeyProperty.isBlank()) {
            if (implementsItgListItem(classDeclaration)) return true
            logger.error(
                "Class ${classDeclaration.qualifiedName?.asString()} must implement ItgListItem " +
                    "or declare a non-empty itemKeyProperty in its Item annotation.",
                classDeclaration,
            )
            return false
        }
        if (!itemKeyProperty.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
            logger.error("itemKeyProperty must be a simple Kotlin property name.", classDeclaration)
            return false
        }
        val property = declaredProperties(classDeclaration)
            .firstOrNull { it.simpleName.asString() == itemKeyProperty }
        if (property == null) {
            logger.error(
                "Class ${classDeclaration.qualifiedName?.asString()} does not declare property $itemKeyProperty.",
                classDeclaration,
            )
            return false
        }
        if (property.type.resolve().nullability == Nullability.NULLABLE) {
            logger.error("itemKeyProperty $itemKeyProperty must be non-null.", property)
            return false
        }
        if (property.isMutable) {
            logger.warn(
                "itemKeyProperty $itemKeyProperty is mutable; changing it breaks Recycler item identity.",
                property,
            )
        }
        return true
    }

    private fun isFragmentClass(classDeclaration: KSClassDeclaration): Boolean =
        isSubclassOf(classDeclaration, FRAGMENT_CLASS)

    private fun isSubclassOf(
        classDeclaration: KSClassDeclaration,
        targetQualifiedName: String,
        visited: MutableSet<String> = mutableSetOf(),
    ): Boolean {
        val currentQualifiedName = classDeclaration.qualifiedName?.asString()
            ?: return false
        if (!visited.add(currentQualifiedName)) return false
        if (currentQualifiedName == targetQualifiedName) return true

        return classDeclaration.superTypes.any { superType ->
            val declaration = superType.resolve().declaration
            val superQualifiedName = declaration.qualifiedName?.asString()
            when {
                superQualifiedName == targetQualifiedName -> true
                declaration is KSClassDeclaration -> isSubclassOf(
                    declaration,
                    targetQualifiedName,
                    visited,
                )
                else -> false
            }
        }
    }

    private fun declaredProperties(classDeclaration: KSClassDeclaration) =
        classDeclaration.declarations.filterIsInstance<KSPropertyDeclaration>()

    private fun readAutoTextField(propertyDeclaration: KSPropertyDeclaration): AutoTextFieldSpec? {
        val annotation = propertyDeclaration.findAnnotation(AUTO_TEXT_FIELD_ANNOTATION) ?: return null
        val propertyName = propertyDeclaration.simpleName.asString()
        return AutoTextFieldSpec(
            propertyName = propertyName,
            viewName = annotation.stringValue("viewName").ifBlank { propertyName },
            payloadKey = annotation.stringValue("payloadKey").ifBlank { propertyName },
        )
    }

    private fun Sequence<KSAnnotation>.hasAnnotation(fqName: String): Boolean =
        any { it.annotationType.resolve().declaration.qualifiedName?.asString() == fqName }

    private fun KSAnnotated.findAnnotation(fqName: String): KSAnnotation? =
        annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == fqName
        }

    private fun KSAnnotation.stringValue(name: String): String =
        arguments.first { it.name?.asString() == name }.value as? String
            ?: error("Missing string value $name")

    private fun KSAnnotation.intValue(name: String): Int =
        when (val value = arguments.first { it.name?.asString() == name }.value) {
            is Int -> value
            is Long -> value.toInt()
            is Short -> value.toInt()
            is Byte -> value.toInt()
            is Number -> value.toInt()
            else -> error("Missing int value $name")
        }

    private fun KSAnnotation.booleanValue(name: String): Boolean =
        arguments.first { it.name?.asString() == name }.value as? Boolean
            ?: error("Missing boolean value $name")

    private fun fieldBindingExpression(field: AutoTextFieldSpec): String =
        "${field.viewName}.text = item.${field.propertyName}.toString()"

    private fun escapeKotlinString(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
