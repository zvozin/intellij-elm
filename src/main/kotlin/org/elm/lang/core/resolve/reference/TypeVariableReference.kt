package org.elm.lang.core.resolve.reference

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.ElmNamedElement
import org.elm.lang.core.psi.ancestorsStrict
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.resolve.ElmReferenceElement

/**
 * A reference to a type variable
 *
 * A type variable is any lower-case name in a type annotation, or a lower-case name defined on the
 * left-hand side of a union type or type alias declaration.
 *
 * e.g. the `a` in `type alias User a = { a | name : String }`
 * e.g. all `a`s in `foo : a -> { f : a } -> a`
 *
 * In function annotations, all variables reference the first variable with the same name.
 *
 * **NOTE** A reference may not resolve in cases where the type variable is the first of it's
 * name in an annotation
 *
 * e.g. the `a` in `foo : a -> ()` or the `a` in `foo : { a | f : () } -> ()`
 *
 * **NOTE** In an annotation like this: `a -> { a | f : a } -> a`, it's not clear how the
 * references should work. The Elm 0.19 compiler crashes if you try to call a function with this
 * annotation.
 */
class TypeVariableReference(element: ElmReferenceElement)
    : ElmReferenceCached<ElmReferenceElement>(element) {
    override fun isSoft(): Boolean {
        val decl = declaration()
        return decl == null || element is ElmRecordBaseIdentifier && decl is ElmTypeAnnotation
    }

    override fun getVariants(): Array<ElmNamedElement> {
        val decl = declaration()

        return when (decl) {
            is ElmTypeAliasDeclaration -> decl.lowerTypeNameList
            is ElmTypeDeclaration -> decl.lowerTypeNameList
            is ElmTypeAnnotation -> typeAnnotationVariants(decl)
            else -> emptyList()
        }.toTypedArray()
    }

    private fun typeAnnotationVariants(annotation: ElmTypeAnnotation): List<ElmNamedElement> {
        val parents = annotation.ancestorsStrict.takeWhile { it !is ElmFile }
                .filterIsInstance<ElmValueDeclaration>()
                .mapNotNull { it.typeAnnotation }
        return (sequenceOf(annotation) + parents)
                .mapNotNull { anno ->
                    anno.typeExpression?.let {
                        PsiTreeUtil.collectElementsOfType(it, ElmTypeVariable::class.java)
                    }
                }
                .toList().asReversed()
                .flatten()
    }

    override fun resolveInner(): ElmNamedElement? =
            variants.find { it.name == element.referenceName }

    private fun declaration(): PsiElement? {
        return element.ancestorsStrict.takeWhile { it !is ElmFile }.firstOrNull {
            it is ElmTypeAliasDeclaration || it is ElmTypeDeclaration || it is ElmTypeAnnotation
        }
    }
}
