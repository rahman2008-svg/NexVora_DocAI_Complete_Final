/*
 * Copyright 2026 NexVora Lab's Ofc
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.nexvora.docai.core.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class NxTextTest {
    @Test fun formatsBytes() {
        assertThat(formatBytes(512)).isEqualTo("512 B")
        assertThat(formatBytes(2048)).isEqualTo("2 KB")
        assertThat(formatBytes(2_621_440)).isEqualTo("2.5 MB")
    }

    @Test fun sanitizesFileNamesButKeepsBangla() {
        assertThat(sanitizeFileName("a/b:c?.pdf")).isEqualTo("a_b_c_.pdf")
        assertThat(sanitizeFileName("   ")).isEqualTo("document")
        assertThat(sanitizeFileName("নোট")).isEqualTo("নোট")
    }

    @Test fun splitsExtensions() {
        assertThat(splitExtension("report.final.PDF")).isEqualTo("report.final" to "pdf")
        assertThat(splitExtension("README")).isEqualTo("README" to "")
        assertThat(splitExtension(".hidden")).isEqualTo(".hidden" to "")
    }

    @Test fun mapsMime() {
        assertThat(mimeFromExtension("pdf")).isEqualTo("application/pdf")
        assertThat(mimeFromExtension("xyz")).isEqualTo("application/octet-stream")
    }
}
