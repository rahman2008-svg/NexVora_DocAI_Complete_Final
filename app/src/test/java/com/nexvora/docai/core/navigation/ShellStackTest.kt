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
package com.nexvora.docai.core.navigation

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ShellStackTest {
    @Test fun pushAndPop() {
        val s = ShellStack(listOf(NxRoute.Home)).push(NxRoute.Vault)
        assertThat(s.current).isEqualTo(NxRoute.Vault)
        assertThat(s.pop()!!.current).isEqualTo(NxRoute.Home)
        assertThat(ShellStack(listOf(NxRoute.Home)).pop()).isNull()
    }

    @Test fun pushingSameRouteTwiceDoesNotDuplicate() {
        val s = ShellStack(listOf(NxRoute.Home)).push(NxRoute.Ocr).push(NxRoute.Ocr)
        assertThat(s.routes).containsExactly(NxRoute.Home, NxRoute.Ocr)
    }

    @Test fun tabSelectionKeepsHomeBelow() {
        val s = ShellStack(listOf(NxRoute.Home, NxRoute.Ocr)).selectTab(NxRoute.Settings)
        assertThat(s.routes).containsExactly(NxRoute.Home, NxRoute.Settings)
        assertThat(s.selectTab(NxRoute.Home).routes).containsExactly(NxRoute.Home)
    }

    @Test fun serializesAndSurvivesGarbage() {
        val s = ShellStack(listOf(NxRoute.Home, NxRoute.PdfStudio))
        assertThat(ShellStack.parse(s.serialize())).isEqualTo(s)
        assertThat(ShellStack.parse("nonsense").routes).containsExactly(NxRoute.Home)
    }

    @Test fun initialStates() {
        assertThat(ShellStack.initial().current).isEqualTo(NxRoute.Splash)
        assertThat(ShellStack.initial(scannerFirst = true).current).isEqualTo(NxRoute.Scanner)
    }
}
