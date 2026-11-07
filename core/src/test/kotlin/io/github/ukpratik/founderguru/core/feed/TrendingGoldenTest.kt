// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.feed

import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.core.tag.Sector
import io.github.ukpratik.founderguru.core.tag.SectorMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * What's-hot golden tests (US-006, LLD §8). Each JSON file under `trending/` holds an input row set, `now`, and the expected
 * strip; the expected strips are reviewed by hand. `-Pfg.updateGolden=true` rewrites them from the current code.
 */
class TrendingGoldenTest {
    private val dir = File(requireNotNull(javaClass.getResource("/trending")).toURI())
    private val update = System.getProperty("fg.updateGolden") == "true"
    private val json = Json { prettyPrint = true }

    @Test fun `golden strips`() {
        val files = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        assertWithMessage("golden files").that(files.size).isAtLeast(6)
        for (file in files) {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            val now = Instant.parse(root.getValue("now").jsonPrimitive.content).toEpochMilli()
            val rows = root.getValue("rows").jsonArray.map { row(it.jsonObject) }
            val actual = Trending.compute(rows, now).map { it.sector.name to it.count }
            if (update) {
                val expected = JsonArray(actual.map { (s, c) -> buildJsonObject { put("sector", JsonPrimitive(s)); put("count", JsonPrimitive(c)) } })
                val sourceFile = File("src/test/resources/trending/${file.name}")
                sourceFile.writeText(json.encodeToString(JsonObject.serializer(), JsonObject(root + ("expected" to expected))) + "\n")
                continue
            }
            val expected = root.getValue("expected").jsonArray.map {
                it.jsonObject.getValue("sector").jsonPrimitive.content to it.jsonObject.getValue("count").jsonPrimitive.int
            }
            assertWithMessage(file.name).that(actual).containsExactlyElementsIn(expected).inOrder()
            // Determinism: shuffled input gives the same strip.
            assertWithMessage("${file.name} shuffled").that(Trending.compute(rows.shuffled(kotlin.random.Random(7)), now).map { it.sector.name to it.count })
                .isEqualTo(actual)
        }
    }

    private fun row(o: JsonObject): StoryRow {
        val title = o["title"]?.jsonPrimitive?.content
        val sectors = title?.let { SectorMatcher.sectors(it) }
            ?: o.getValue("sectors").jsonArray.fold(0L) { acc, s -> acc or Sector.valueOf(s.jsonPrimitive.content).bit }
        return StoryRow(
            id = o.getValue("id").jsonPrimitive.long, sourceId = o.getValue("sourceId").jsonPrimitive.content, sourceName = "",
            url = "https://x/${o.getValue("id").jsonPrimitive.long}", title = title ?: "", snippet = "", imageUrl = null,
            publishedAt = Instant.parse(o.getValue("publishedAt").jsonPrimitive.content).toEpochMilli(), firstSeenAt = 0,
            lenses = 0, sectors = sectors, regionMentions = 0, savedAt = null, openedAt = null,
            viaGlobal = o["viaGlobal"]?.jsonPrimitive?.boolean ?: false,
        )
    }
}
