package kotlinx.coroutines.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkedListTest {
    data class IntNode(val i: Int) : LockFreeLinkedListNode()

    @Test
    fun testSimpleAddLastRemove() {
        val list = LockFreeLinkedListHead()
        assertContents(list)
        val n1 = IntNode(1).apply { list.addLast(this, Int.MAX_VALUE) }
        assertContents(list, 1)
        val n2 = IntNode(2).apply { list.addLast(this, Int.MAX_VALUE) }
        assertContents(list, 1, 2)
        val n3 = IntNode(3).apply { list.addLast(this, Int.MAX_VALUE) }
        assertContents(list, 1, 2, 3)
        val n4 = IntNode(4).apply { list.addLast(this, Int.MAX_VALUE) }
        assertContents(list, 1, 2, 3, 4)
        assertTrue(n1.remove())
        assertContents(list, 2, 3, 4)
        assertTrue(n3.remove())
        assertContents(list, 2, 4)
        assertTrue(n4.remove())
        assertContents(list, 2)
        assertTrue(n2.remove())
        assertFalse(n2.remove())
        assertContents(list)
    }

    private fun assertContents(list: LockFreeLinkedListHead, vararg expected: Int) {
        val n = expected.size
        val actual = IntArray(n)
        var index = 0
        list.forEach { if (it is IntNode) actual[index++] = it.i }
        assertEquals(n, index)
        for (i in 0 until n) assertEquals(expected[i], actual[i], "item i")
    }
}
