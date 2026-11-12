package app.gildongmu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {

    @Test
    fun readsNestedValues() {
        val root = Json.parseObject(
            """
            { "name": "길동무", "count": 3, "ratio": -1.5e1, "on": true, "none": null,
              "list": ["가", "나"], "nested": { "inner": [ { "x": 1 }, { "x": 2 } ] } }
            """
        )
        assertEquals("길동무", root.string("name"))
        assertEquals(3, root.int("count"))
        assertEquals(-15.0, root.double("ratio"), 0.0)
        assertEquals(true, root.optBoolean("on"))
        assertNull(root.optString("none"))
        assertEquals(listOf("가", "나"), root.strings("list"))
        assertEquals(listOf(1, 2), root.obj("nested").objects("inner").map { it.int("x") })
        assertTrue(root.array("missing").isEmpty())
    }

    @Test
    fun readsEscapes() {
        val root = Json.parseObject(""" { "s": "줄\n바꿈 \"따옴표\" \\ \/ é 가" } """)
        assertEquals("줄\n바꿈 \"따옴표\" \\ / é 가", root.string("s"))
    }

    @Test
    fun malformedTextReportsLineAndColumn() {
        try {
            Json.parse("{\n  \"a\": 1,\n  \"b\" 2\n}")
            fail("Expected a JsonException")
        } catch (e: JsonException) {
            assertTrue(e.message, e.message!!.contains("line 3"))
        }
    }

    @Test
    fun rejectsTrailingText() {
        try {
            Json.parse("{} extra")
            fail("Expected a JsonException")
        } catch (e: JsonException) {
            assertTrue(e.message!!.contains("Unexpected text"))
        }
    }

    @Test
    fun errorsNameTheFullPathOfTheField() {
        val root = Json.parseObject(""" { "pois": [ { "story": { "variants": [ { "seconds": "soon" } ] } } ] } """)
        try {
            root.objects("pois")[0].obj("story").objects("variants")[0].int("seconds")
            fail("Expected a JsonException")
        } catch (e: JsonException) {
            assertEquals("Field \"pois[0].story.variants[0].seconds\" should be a number", e.message)
        }
        try {
            root.objects("pois")[0].obj("story").string("title")
            fail("Expected a JsonException")
        } catch (e: JsonException) {
            assertEquals("Missing field \"pois[0].story.title\"", e.message)
        }
    }

    @Test
    fun aFractionIsNotAWholeNumber() {
        try {
            Json.parseObject(""" { "n": 2.5 } """).int("n")
            fail("Expected a JsonException")
        } catch (e: JsonException) {
            assertTrue(e.message!!.contains("whole number"))
        }
    }
}
