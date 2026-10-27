package com.example.vnscreenreader.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptParserTest {

    private val valid = """
        {
          "id": "skip_ad_vn",
          "name": "Bỏ qua quảng cáo",
          "description": "Mẫu",
          "packages": ["com.example.app"],
          "rules": [
            { "text": "Bỏ qua", "match": "contains", "action": "click" },
            { "viewId": "close_button" }
          ]
        }
    """.trimIndent()

    @Test
    fun validScript_isParsed() {
        val def = ScriptParser.parse(valid).getOrThrow()
        assertEquals("skip_ad_vn", def.id)
        assertEquals(listOf("com.example.app"), def.packages)
        assertEquals(2, def.rules.size)
        assertEquals(MatchMode.CONTAINS, def.rules[0].mode)
        assertEquals(RuleAction.CLICK, def.rules[1].action) // mặc định
    }

    @Test
    fun invalidJson_fails() {
        assertTrue(ScriptParser.parse("{ not json").isFailure)
        assertTrue(ScriptParser.parse("").isFailure)
    }

    @Test
    fun invalidId_fails_includingPathTraversal() {
        val bad = valid.replace("skip_ad_vn", "../evil")
        assertTrue(ScriptParser.parse(bad).isFailure)
        assertFalse(ScriptParser.isValidId("Has Space"))
        assertFalse(ScriptParser.isValidId(""))
        assertTrue(ScriptParser.isValidId("ok_id_1"))
    }

    @Test
    fun missingRules_fails() {
        assertTrue(ScriptParser.parse("""{"id":"a","name":"A"}""").isFailure)
        assertTrue(ScriptParser.parse("""{"id":"a","name":"A","rules":[]}""").isFailure)
    }

    @Test
    fun ruleWithoutCriteria_fails() {
        val json = """{"id":"a","name":"A","rules":[{"action":"click"}]}"""
        assertTrue(ScriptParser.parse(json).isFailure)
    }

    @Test
    fun unknownMatchOrAction_fails() {
        assertTrue(ScriptParser.parse("""{"id":"a","name":"A","rules":[{"text":"x","match":"regex"}]}""").isFailure)
        assertTrue(ScriptParser.parse("""{"id":"a","name":"A","rules":[{"text":"x","action":"delete"}]}""").isFailure)
    }

    @Test
    fun tooManyRules_fails() {
        val rules = (1..(ScriptParser.MAX_RULES + 1)).joinToString(",") { """{"text":"t$it"}""" }
        assertTrue(ScriptParser.parse("""{"id":"a","name":"A","rules":[$rules]}""").isFailure)
    }

    @Test
    fun overlongPattern_fails() {
        val long = "x".repeat(ScriptParser.MAX_PATTERN_LENGTH + 1)
        assertTrue(ScriptParser.parse("""{"id":"a","name":"A","rules":[{"text":"$long"}]}""").isFailure)
    }

    @Test
    fun utf8Bom_isTolerated() {
        assertTrue(ScriptParser.parse("\uFEFF" + valid).isSuccess)
    }

    @Test
    fun deeplyNestedJson_isRejectedInsteadOfCrashing() {
        // 60 000 dấu "[" làm org.json trên Android ném StackOverflowError (là Error, không phải Exception).
        val bomb = """{"id":"a","name":"A","x":""" + "[".repeat(60_000) + "]".repeat(60_000) + "}"
        val result = ScriptParser.parse(bomb)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("lồng"))
    }

    @Test
    fun bracketsAndEscapedQuotesInsideStrings_doNotCountAsNesting() {
        val brackets = "[".repeat(30)
        val json = """{"id":"a","name":"$brackets \" $brackets","rules":[{"text":"x"}]}"""
        assertTrue(ScriptParser.parse(json).isSuccess)
    }

    @Test
    fun realisticScript_staysWellBelowNestingLimit() {
        assertFalse(JsonNesting.exceeds(valid, ScriptParser.MAX_NESTING_DEPTH))
        assertTrue(JsonNesting.exceeds("[[[[", 3))
        assertFalse(JsonNesting.exceeds("]]]][[[[", 4)) // dấu đóng thừa không làm độ sâu âm
    }

    @Test
    fun failureMessage_isDescriptive() {
        val error = ScriptParser.parse("""{"id":"a","name":"A"}""").exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("rules"))
    }
}

class ScriptRuleTest {

    private fun rule(
        text: String? = null,
        desc: String? = null,
        viewId: String? = null,
        mode: MatchMode = MatchMode.CONTAINS,
    ) = ScriptRule(text, desc, viewId, mode, RuleAction.CLICK)

    @Test
    fun contains_ignoresCaseAndWhitespace() {
        assertTrue(rule(text = "bỏ qua").matches("  Bỏ qua quảng cáo ", null, null))
    }

    @Test
    fun equals_requiresFullMatch() {
        val r = rule(text = "Đóng", mode = MatchMode.EQUALS)
        assertTrue(r.matches("đóng", null, null))
        assertFalse(r.matches("Đóng quảng cáo", null, null))
    }

    @Test
    fun allDeclaredCriteria_mustMatch() {
        val r = rule(text = "Skip", desc = "ad")
        assertTrue(r.matches("Skip", "Skip ad", null))
        assertFalse(r.matches("Skip", "other", null))
        assertFalse(r.matches("Skip", null, null))
    }

    @Test
    fun viewId_matchesFullOrSuffix() {
        val r = rule(viewId = "close_button")
        assertTrue(r.matches(null, null, "com.example:id/close_button"))
        assertTrue(r.matches(null, null, "close_button"))
        assertFalse(r.matches(null, null, "com.example:id/close_button_2"))
        assertFalse(r.matches(null, null, null))
    }

    @Test
    fun nullNodeText_neverMatchesTextRule() {
        assertFalse(rule(text = "x").matches(null, null, null))
    }

    @Test
    fun vietnamese_composedAndDecomposedForms_matchEachOther() {
        val composed = "Bỏ qua" // "ỏ" dựng sẵn U+1ECF
        val decomposed = "Bỏ qua" // "o" + dấu hỏi tổ hợp U+0309
        assertTrue(rule(text = composed).matches(decomposed, null, null))
        assertTrue(rule(text = decomposed).matches(composed, null, null))
        assertTrue(rule(desc = decomposed, mode = MatchMode.EQUALS).matches(null, composed, null))
    }

    @Test
    fun vietnamese_caseInsensitive() {
        assertTrue(rule(text = "ĐÓNG", mode = MatchMode.EQUALS).matches("đóng", null, null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ruleWithoutCriteria_isRejected() {
        rule()
    }
}
