package dev.jbang.idea.project

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope

/** Resolves Java classes from the exact files declared by //SOURCES. */
class JBangSourceElementFinder(private val project: Project) : PsiElementFinder() {
    override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? =
        findClasses(qualifiedName, scope).firstOrNull()

    override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> =
        classes(scope).filter { it.qualifiedName == qualifiedName }.toList().toTypedArray()

    override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> =
        classes(scope).filter { it.qualifiedName?.substringBeforeLast('.', "") == psiPackage.qualifiedName }.toList().toTypedArray()

    override fun getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set<String> =
        getClasses(psiPackage, scope).mapNotNullTo(mutableSetOf(), PsiClass::getName)

    private fun classes(scope: GlobalSearchScope): Sequence<PsiClass> =
        JBangProjectService.getInstance(project).allSourceFiles()
            .asSequence()
            .filter(scope::contains)
            .mapNotNull { PsiManager.getInstance(project).findFile(it) as? PsiJavaFile }
            .flatMap { it.classes.asSequence() }
}
