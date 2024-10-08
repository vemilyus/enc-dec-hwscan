/**
 * Copyright (C) 2024 Alex Katlein <dev@vemilyus.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.v47.encDecHwscan

import fr.stardustenterprises.yanl.NativeLoader
import io.v47.encDecHwscan.bindings.EncDecDevices
import io.v47.encDecHwscan.bindings.EncDecHwscan
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout

/**
 * Entrypoint for accessing the native library via the Java Foreign Function and Memory API.
 */
object Native {
    private val SCAN_DEVICES_HANDLE by lazy {
        Linker
            .nativeLinker()
            .downcallHandle(
                SymbolLookup.loaderLookup().find("scan_devices").orElseThrow(),
                FunctionDescriptor.of(EncDecHwscan.C_INT, EncDecHwscan.C_POINTER)
            )
    }

    private val FREE_DEVICES_HANDLE by lazy {
        Linker
            .nativeLinker()
            .downcallHandle(
                SymbolLookup.loaderLookup().find("free_devices").orElseThrow(),
                FunctionDescriptor.ofVoid(EncDecHwscan.C_POINTER)
            )
    }

    /**
     * Loads the native library.
     */
    fun load() {
        NativeLoader
            .Builder().build()
            .loadLibrary("enc_dec_hwscan")
    }

    /**
     * Calls the native library and takes care of memory allocation and clean-up.
     */
    fun <T : Any> scanDevices(mapper: (MemorySegment) -> T) =
        Arena.ofConfined().use { arena ->
            var supportInfo: MemorySegment? = null

            try {
                val target = arena.allocate(ValueLayout.ADDRESS.withoutTargetLayout())
                val errno = SCAN_DEVICES_HANDLE.invokeExact(target) as Int

                if (errno == 0) {
                    supportInfo = target
                        .get(ValueLayout.ADDRESS, 0L)
                        .reinterpret(EncDecDevices.layout().byteSize())

                    mapper(supportInfo) to 0
                } else
                    null to errno
            } finally {
                supportInfo?.let { FREE_DEVICES_HANDLE.invokeExact(it) as Unit }
            }
        }
}
