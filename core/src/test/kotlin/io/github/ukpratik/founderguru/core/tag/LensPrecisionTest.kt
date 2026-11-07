// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.tag

import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.founderguru.core.TestFeeds
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.core.sources.CatalogLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Lens precision gate (US-005, goal G2, LLD §8). `lens-testset.json` holds 150 real items labelled before any rule was
 * written: commit be0743e ("test: add hand-labelled lens test set ... before any rules"). Tags each item with the real
 * [Tagger] (source default + keyword rules) and asserts precision ≥ 0.75 per lens with at least 15 positives per lens.
 * Recall and the confusion counts are printed, not gated.
 */
class LensPrecisionTest {
    private data class Labelled(val id: String, val source: SourceDef, val title: String, val snippet: String, val labels: Set<Lens>)

    private val sources = CatalogLoader.load(TestFeeds.shippedSourcesJson()).sources.associateBy { it.id }

    private val items: List<Labelled> = run {
        val text = requireNotNull(javaClass.getResourceAsStream("/lens-testset.json")).bufferedReader().readText()
        Json.parseToJsonElement(text).jsonObject.getValue("items").jsonArray.map { e ->
            val o = e.jsonObject
            Labelled(
                id = o.getValue("id").jsonPrimitive.content,
                source = sources.getValue(o.getValue("sourceId").jsonPrimitive.content),
                title = o.getValue("title").jsonPrimitive.content,
                snippet = o.getValue("snippet").jsonPrimitive.content,
                labels = o.getValue("labels").jsonArray.map { Lens.valueOf(it.jsonPrimitive.content) }.toSet(),
            )
        }
    }

    @Test fun `precision is at least 75 percent per lens`() {
        assertWithMessage("test set size").that(items.size).isEqualTo(150)
        val report = StringBuilder("\nLens        positives  tagged  TP  FP  FN  precision  recall\n")
        val failures = mutableListOf<String>()
        for (lens in Lens.entries) {
            var tp = 0
            var fp = 0
            var fn = 0
            val fpIds = mutableListOf<String>()
            for (item in items) {
                val tagged = Tagger.lenses(item.title, item.snippet, item.source) and lens.bit != 0
                val positive = lens in item.labels
                when {
                    tagged && positive -> tp++
                    tagged -> fp++.also { fpIds += item.id }
                    positive -> fn++
                }
            }
            val positives = tp + fn
            val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
            val recall = if (positives == 0) 0.0 else tp.toDouble() / positives
            report.append("%-11s %9d %7d %3d %3d %3d %10.2f %7.2f\n".format(lens, positives, tp + fp, tp, fp, fn, precision, recall))
            if (fpIds.isNotEmpty()) report.append("  false positives: ${fpIds.joinToString()}\n")
            if (positives < 15) failures += "$lens has $positives positives (< 15)"
            if (precision < 0.75) failures += "$lens precision %.2f < 0.75".format(precision)
        }
        println(report)
        assertWithMessage(report.toString()).that(failures).isEmpty()
    }
}
