package dev.jbang.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType


class JbangCatalogJsonSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): MutableList<JsonSchemaFileProvider> {
        return mutableListOf(JBangCatalogJsonSchemaFileProvider())
    }
}

class JBangCatalogJsonSchemaFileProvider : JsonSchemaFileProvider {

    override fun isAvailable(file: VirtualFile): Boolean {
        return file.name == "jbang-catalog.json"
    }

    override fun getName(): String {
        return "JBang"
    }

    override fun getSchemaFile(): VirtualFile? {
        return JsonSchemaProviderFactory.getResourceFile(JBangCatalogJsonSchemaFileProvider::class.java, "/jbang-catalog-schema.json");
    }

    override fun getSchemaType(): SchemaType {
        return SchemaType.embeddedSchema
    }

    override fun getPresentableName(): String {
        return "JBang"
    }

    override fun getRemoteSource(): String {
        return "https://www.jbang.dev/documentation/guide/latest/alias_catalogs.html"
    }
}