package dev.jbang.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

class JBangCatalogJsonSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(JBangCatalogJsonSchemaProvider())
    }
}

private class JBangCatalogJsonSchemaProvider : JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean = file.name == "jbang-catalog.json"
    override fun getName(): String = "JBang Catalog"
    override fun getSchemaFile(): VirtualFile? =
        JsonSchemaProviderFactory.getResourceFile(JBangCatalogJsonSchemaProvider::class.java, "/jbang-catalog-schema.json")
    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
}
