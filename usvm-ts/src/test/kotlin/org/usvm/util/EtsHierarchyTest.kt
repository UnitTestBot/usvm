package org.usvm.util

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class EtsHierarchyTest {
    @Test
    fun `self interface reference retains superclass and SDK Object`() {
        val base = clazz(name = "BaseCollection")
        val treeSignature = signature(name = "BinarySearchTree")
        val tree = clazz(name = "BinarySearchTree", superClass = base.signature, interfaces = listOf(treeSignature))
        val sdkObject = clazz(name = "Object", fileName = "lib.es5.d.ts")
        val hierarchy = hierarchy(classes = listOf(base, tree), sdkClasses = listOf(sdkObject))

        val ancestors = hierarchy.getAncestors(tree)
        val baseInheritors = hierarchy.getInheritors(base)

        assertEquals(setOf(tree, base, sdkObject), ancestors)
        assertEquals(setOf(tree, base), baseInheritors)
    }

    @Test
    fun `mutual superclass cycle retains additional interface ancestors`() {
        val root = clazz(name = "Root")
        val first = clazz(name = "First", superClass = signature(name = "Second"))
        val second = clazz(name = "Second", superClass = first.signature, interfaces = listOf(root.signature))
        val hierarchy = hierarchy(classes = listOf(first, second, root))

        val firstAncestors = hierarchy.getAncestors(first)
        val secondAncestors = hierarchy.getAncestors(second)
        val rootInheritors = hierarchy.getInheritors(root)

        assertEquals(setOf(first, second, root), firstAncestors)
        assertEquals(setOf(first, second, root), secondAncestors)
        assertEquals(setOf(first, second, root), rootInheritors)
    }

    @Test
    fun `interface cycles retain every reachable parent`() {
        val root = clazz(name = "Root", category = EtsClassCategory.INTERFACE)
        val first = clazz(
            name = "First",
            category = EtsClassCategory.INTERFACE,
            interfaces = listOf(signature(name = "Second")),
        )
        val second = clazz(
            name = "Second",
            category = EtsClassCategory.INTERFACE,
            interfaces = listOf(first.signature, root.signature),
        )
        val hierarchy = hierarchy(classes = listOf(first, second, root))

        val ancestors = hierarchy.getAncestors(first)

        assertEquals(setOf(first, second, root), ancestors)
    }

    @Test
    fun `unknown file retains all same-name candidates despite a cyclic branch`() {
        val root = clazz(name = "Root")
        val firstSignature = signature(name = "Shared", fileName = "first.ts")
        val first = clazz(name = "Shared", fileName = "first.ts", interfaces = listOf(firstSignature))
        val second = clazz(name = "Shared", fileName = "second.ts", superClass = root.signature)
        val leaf = clazz(
            name = "Leaf",
            interfaces = listOf(EtsClassSignature(name = "Shared", file = EtsFileSignature.UNKNOWN)),
        )
        val hierarchy = hierarchy(classes = listOf(leaf, first, second, root))

        val ancestors = hierarchy.getAncestors(leaf)

        assertEquals(setOf(leaf, first, second, root), ancestors)
    }

    @Test
    fun `exact file retains only its own same-name ancestor`() {
        val first = clazz(name = "Shared", fileName = "first.ts")
        val second = clazz(name = "Shared", fileName = "second.ts")
        val leaf = clazz(name = "Leaf", superClass = second.signature)
        val hierarchy = hierarchy(classes = listOf(leaf, first, second))

        val ancestors = hierarchy.getAncestors(leaf)

        assertEquals(setOf(leaf, second), ancestors)
    }

    @Test
    fun `diamond ancestors are complete and unique`() {
        val root = clazz(name = "Root")
        val first = clazz(name = "First", superClass = root.signature)
        val second = clazz(name = "Second", superClass = root.signature)
        val leaf = clazz(name = "Leaf", superClass = first.signature, interfaces = listOf(second.signature))
        val hierarchy = hierarchy(classes = listOf(leaf, first, second, root))

        val ancestors = hierarchy.getAncestors(leaf)

        assertEquals(setOf(leaf, first, second, root), ancestors)
    }

    @Test
    fun `missing exact signature still fails instead of choosing another file`() {
        val parent = clazz(name = "Shared", fileName = "first.ts")
        val leaf = clazz(name = "Leaf", superClass = signature(name = "Shared", fileName = "missing.ts"))
        val hierarchy = hierarchy(classes = listOf(leaf, parent))

        assertFailsWith<IllegalStateException> {
            hierarchy.getAncestors(leaf)
        }
    }

    @Test
    fun `duplicate runtime declarations still fail instead of choosing one`() {
        val first = clazz(name = "Duplicate")
        val second = clazz(name = "Duplicate")
        val leaf = clazz(name = "Leaf", superClass = first.signature)
        val hierarchy = hierarchy(classes = listOf(leaf, first, second))

        assertFailsWith<IllegalArgumentException> {
            hierarchy.getAncestors(leaf)
        }
    }

    @Test
    fun `cyclic ancestry terminates and keeps first encounter order`() {
        val fileSignature = EtsFileSignature(projectName = "test", fileName = "cycle.ts")
        val aSignature = EtsClassSignature(name = "A", file = fileSignature)
        val bSignature = EtsClassSignature(name = "B", file = fileSignature)
        val unrelatedSignature = EtsClassSignature(name = "Unrelated", file = fileSignature)
        val a = EtsClassImpl(
            signature = aSignature,
            fields = emptyList(),
            methods = emptyList(),
            superClass = bSignature,
        )
        val b = EtsClassImpl(
            signature = bSignature,
            fields = emptyList(),
            methods = emptyList(),
            superClass = aSignature,
        )
        val unrelated = EtsClassImpl(
            signature = unrelatedSignature,
            fields = emptyList(),
            methods = emptyList(),
        )
        val file = EtsFile(signature = fileSignature, classes = listOf(a, b, unrelated), namespaces = emptyList())
        val hierarchy = EtsHierarchy(EtsScene(projectFiles = listOf(file)))

        val unrelatedAncestors = hierarchy.getAncestors(unrelated)
        val aAncestors = hierarchy.getAncestors(a)
        val bAncestors = hierarchy.getAncestors(b)

        assertEquals(listOf(unrelated), unrelatedAncestors.toList())
        assertEquals(listOf(a, b), aAncestors.toList())
        assertEquals(listOf(b, a), bAncestors.toList())
    }

    private fun signature(name: String, fileName: String = "hierarchy.ts") = EtsClassSignature(
        name = name,
        file = EtsFileSignature(projectName = "test", fileName = fileName),
    )

    private fun clazz(
        name: String,
        fileName: String = "hierarchy.ts",
        category: EtsClassCategory = EtsClassCategory.CLASS,
        superClass: EtsClassSignature? = null,
        interfaces: List<EtsClassSignature> = emptyList(),
    ): EtsClass = EtsClassImpl(
        signature = signature(name = name, fileName = fileName),
        fields = emptyList(),
        methods = emptyList(),
        category = category,
        superClass = superClass,
        implementedInterfaces = interfaces,
    )

    private fun hierarchy(classes: List<EtsClass>, sdkClasses: List<EtsClass> = emptyList()): EtsHierarchy {
        fun files(classes: List<EtsClass>) = classes.groupBy { it.signature.file }.map { (signature, declarations) ->
            EtsFile(signature = signature, classes = declarations, namespaces = emptyList())
        }

        return EtsHierarchy(EtsScene(projectFiles = files(classes), sdkFiles = files(sdkClasses)))
    }
}
