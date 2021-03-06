/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin

import com.intellij.core.CoreFileTypeRegistry
import com.intellij.mock.MockProject
import com.intellij.openapi.fileTypes.FileTypeRegistry
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.scripting.legacy.CliScriptDependenciesProvider
import org.jetbrains.kotlin.scripting.legacy.CliScriptReportSink
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.extensions.CompilerConfigurationExtension
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.script.ScriptDefinitionProvider
import org.jetbrains.kotlin.script.ScriptDependenciesProvider
import org.jetbrains.kotlin.script.ScriptReportSink
import org.jetbrains.kotlin.script.StandardScriptDefinition
import org.jetbrains.kotlin.scripting.legacy.CliScriptDefinitionProvider
import java.io.File

class ScriptingCompilerConfigurationExtension(val project: MockProject) : CompilerConfigurationExtension {

    override fun updateConfiguration(configuration: CompilerConfiguration) {

        if (!configuration.getBoolean(ScriptingConfigurationKeys.DISABLE_SCRIPTING_PLUGIN_OPTION)) {

            val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY) ?: MessageCollector.NONE
            val projectRoot = project.run { basePath ?: baseDir?.canonicalPath }?.let(::File)
            if (projectRoot != null) {
                configuration.put(
                    ScriptingConfigurationKeys.LEGACY_SCRIPT_RESOLVER_ENVIRONMENT_OPTION,
                    "projectRoot",
                    projectRoot
                )
            }
            val scriptResolverEnv = configuration.getMap(ScriptingConfigurationKeys.LEGACY_SCRIPT_RESOLVER_ENVIRONMENT_OPTION)

            val explicitScriptDefinitions = configuration.getList(ScriptingConfigurationKeys.SCRIPT_DEFINITIONS)

            if (explicitScriptDefinitions.isNotEmpty()) {
                configureScriptDefinitions(
                    explicitScriptDefinitions,
                    configuration,
                    this::class.java.classLoader,
                    messageCollector,
                    scriptResolverEnv
                )
            }
            // If not disabled explicitly, we should always support at least the standard script definition
            if (!configuration.getBoolean(JVMConfigurationKeys.DISABLE_STANDARD_SCRIPT_DEFINITION) &&
                !configuration.getList(JVMConfigurationKeys.SCRIPT_DEFINITIONS).contains(StandardScriptDefinition)
            ) {
                configuration.add(JVMConfigurationKeys.SCRIPT_DEFINITIONS, StandardScriptDefinition)
            }

            configuration.add(
                JVMConfigurationKeys.SCRIPT_DEFINITIONS_SOURCES,
                ScriptDefinitionsFromClasspathDiscoverySource(
                    configuration.jvmClasspathRoots,
                    configuration.get(ScriptingConfigurationKeys.LEGACY_SCRIPT_RESOLVER_ENVIRONMENT_OPTION) ?: emptyMap(),
                    messageCollector
                )
            )
        }

        // If not disabled explicitly, we should always support at least the standard script definition
        if (!configuration.getBoolean(JVMConfigurationKeys.DISABLE_STANDARD_SCRIPT_DEFINITION) &&
            StandardScriptDefinition !in configuration.getList(JVMConfigurationKeys.SCRIPT_DEFINITIONS)
        ) {
            configuration.add(JVMConfigurationKeys.SCRIPT_DEFINITIONS, StandardScriptDefinition)
        }

        val scriptDefinitionProvider = ScriptDefinitionProvider.getInstance(project) as? CliScriptDefinitionProvider
        if (scriptDefinitionProvider != null) {
            scriptDefinitionProvider.setScriptDefinitionsSources(configuration.getList(JVMConfigurationKeys.SCRIPT_DEFINITIONS_SOURCES))
            scriptDefinitionProvider.setScriptDefinitions(configuration.getList(JVMConfigurationKeys.SCRIPT_DEFINITIONS))

            // Register new file extensions
            val fileTypeRegistry = FileTypeRegistry.getInstance() as CoreFileTypeRegistry

            scriptDefinitionProvider.getKnownFilenameExtensions().filter {
                fileTypeRegistry.getFileTypeByExtension(it) != KotlinFileType.INSTANCE
            }.forEach {
                fileTypeRegistry.registerFileType(KotlinFileType.INSTANCE, it)
            }
        }

    }
}

class ScriptingCompilerConfigurationComponentRegistrar : ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        CompilerConfigurationExtension.registerExtension(project, ScriptingCompilerConfigurationExtension(project))

        val scriptDefinitionProvider = CliScriptDefinitionProvider()
        project.registerService(ScriptDefinitionProvider::class.java, scriptDefinitionProvider)
        project.registerService(
            ScriptDependenciesProvider::class.java,
            CliScriptDependenciesProvider(project)
        )
        SyntheticResolveExtension.registerExtension(project, ScriptingResolveExtension())

        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY)
        if (messageCollector != null) {
            project.registerService(ScriptReportSink::class.java,
                                    CliScriptReportSink(messageCollector)
            )
        }
    }
}


fun configureScriptDefinitions(
    scriptTemplates: List<String>,
    configuration: CompilerConfiguration,
    baseClassloader: ClassLoader,
    messageCollector: MessageCollector,
    scriptResolverEnv: Map<String, Any?>
) {
    // TODO: consider using escaping to allow kotlin escaped names in class names
    val templatesFromClasspath = loadScriptTemplatesFromClasspath(
        scriptTemplates, configuration.jvmClasspathRoots, emptyList(), baseClassloader, scriptResolverEnv, messageCollector
    )
    configuration.addAll(JVMConfigurationKeys.SCRIPT_DEFINITIONS, templatesFromClasspath.toList())
}

