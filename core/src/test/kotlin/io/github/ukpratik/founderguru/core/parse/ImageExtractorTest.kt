// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.model.MediaRef
import io.github.ukpratik.founderguru.core.model.MediaRef.Kind
import io.github.ukpratik.founderguru.core.model.ParsedItem
import org.junit.Test

class ImageExtractorTest {
    private fun extract(vararg media: MediaRef, description: String? = null, encoded: String? = null) =
        ImageExtractor.extract(ParsedItem(media = media.toList(), description = description, encodedContent = encoded))

    @Test fun `priority - media content, thumbnail, enclosure, then img tags`() {
        val content = MediaRef(Kind.MEDIA_CONTENT, "https://a/c.jpg", medium = "image")
        val thumb = MediaRef(Kind.MEDIA_THUMBNAIL, "https://a/t.jpg")
        val enc = MediaRef(Kind.ENCLOSURE, "https://a/e.jpg", type = "image/jpeg")
        assertThat(extract(enc, thumb, content)).isEqualTo("https://a/c.jpg")
        assertThat(extract(enc, thumb)).isEqualTo("https://a/t.jpg")
        assertThat(extract(enc, description = "<img src='https://a/d.jpg'>")).isEqualTo("https://a/e.jpg")
        assertThat(extract(description = "<p>x</p>", encoded = "<img src=\"https://a/x.png\">")).isEqualTo("https://a/x.png")
    }

    @Test fun `media content must be an image`() {
        assertThat(extract(MediaRef(Kind.MEDIA_CONTENT, "https://a/v.mp4", medium = "video"))).isNull()
        assertThat(extract(MediaRef(Kind.MEDIA_CONTENT, "https://a/p.webp"))).isEqualTo("https://a/p.webp")
        assertThat(extract(MediaRef(Kind.ENCLOSURE, "https://a/a.mp3", type = "audio/mpeg"))).isNull()
    }

    @Test fun `skips tracking pixels, data URIs and non-http URLs`() {
        assertThat(extract(description = "<img src=\"https://t/p.gif\" width=\"1\" height=\"1\"><img src=\"data:image/png;base64,xx\"><img src=\"ftp://a/b.jpg\"><img src=\"https://a/real.jpg\">"))
            .isEqualTo("https://a/real.jpg")
        assertThat(extract(MediaRef(Kind.MEDIA_THUMBNAIL, "https://t/p.gif", width = "1"))).isNull()
    }

    @Test fun `http is upgraded to https and entities in src are decoded`() {
        assertThat(extract(description = "<img src=\"http://a.com/x.jpg?w=1&amp;h=2\">")).isEqualTo("https://a.com/x.jpg?w=1&h=2")
    }
}
