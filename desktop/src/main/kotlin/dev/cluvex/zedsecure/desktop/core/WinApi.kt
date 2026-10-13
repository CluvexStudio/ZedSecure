package dev.cluvex.zedsecure.desktop.core

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference

internal object WinApi {
    const val SEE_MASK_NOCLOSEPROCESS = 0x00000040
    const val SEE_MASK_NOASYNC = 0x00000100
    const val SEE_MASK_FLAG_NO_UI = 0x00000400
    const val SW_HIDE = 0
    const val ERROR_CANCELLED = 1223
    const val CTRL_C_EVENT = 0
    const val WAIT_OBJECT_0 = 0

    @Structure.FieldOrder(
        "cbSize", "fMask", "hwnd", "lpVerb", "lpFile", "lpParameters", "lpDirectory", "nShow", "hInstApp",
        "lpIDList", "lpClass", "hkeyClass", "dwHotKey", "hMonitor", "hProcess",
    )
    class ShellExecuteInfo : Structure() {
        @JvmField var cbSize: Int = 0
        @JvmField var fMask: Int = 0
        @JvmField var hwnd: Pointer? = null
        @JvmField var lpVerb: WString? = null
        @JvmField var lpFile: WString? = null
        @JvmField var lpParameters: WString? = null
        @JvmField var lpDirectory: WString? = null
        @JvmField var nShow: Int = 0
        @JvmField var hInstApp: Pointer? = null
        @JvmField var lpIDList: Pointer? = null
        @JvmField var lpClass: WString? = null
        @JvmField var hkeyClass: Pointer? = null
        @JvmField var dwHotKey: Int = 0
        @JvmField var hMonitor: Pointer? = null
        @JvmField var hProcess: Pointer? = null
    }

    interface Shell32 : Library {
        fun ShellExecuteExW(info: ShellExecuteInfo): Boolean
    }

    interface Kernel32 : Library {
        fun AttachConsole(processId: Int): Boolean
        fun FreeConsole(): Boolean
        fun GenerateConsoleCtrlEvent(ctrlEvent: Int, processGroupId: Int): Boolean
        fun SetConsoleCtrlHandler(handler: Callback?, add: Boolean): Boolean
        fun WaitForSingleObject(handle: Pointer, millis: Int): Int
        fun GetExitCodeProcess(handle: Pointer, exitCode: IntByReference): Boolean
        fun CloseHandle(handle: Pointer): Boolean
    }

    interface IpHelper : Library {
        fun GetBestInterfaceEx(destination: Pointer, bestIfIndex: IntByReference): Int
        fun ConvertInterfaceIndexToLuid(index: Int, luid: LongByReference): Int
        fun ConvertInterfaceLuidToAlias(luid: LongByReference, alias: CharArray, length: Long): Int
        fun GetBestRoute(destination: Int, source: Int, bestRoute: Pointer): Int
    }

    interface Advapi32 : Library {
        fun RegCreateKeyExW(
            key: Pointer, subKey: WString, reserved: Int, className: WString?, options: Int, desired: Int,
            security: Pointer?, result: PointerByReference, disposition: IntByReference?,
        ): Int
        fun RegSetValueExW(key: Pointer, name: WString, reserved: Int, type: Int, data: ByteArray, size: Int): Int
        fun RegDeleteTreeW(key: Pointer, subKey: WString): Int
        fun RegCloseKey(key: Pointer): Int
    }

    val shell32: Shell32 by lazy { Native.load("shell32", Shell32::class.java) }
    val kernel32: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
    val ipHelper: IpHelper by lazy { Native.load("iphlpapi", IpHelper::class.java) }
    val advapi32: Advapi32 by lazy { Native.load("advapi32", Advapi32::class.java) }

    private val HKEY_LOCAL_MACHINE = Pointer(-2147483646L)
    private const val KEY_ALL_ACCESS_64 = 0xF003F or 0x0100
    private const val REG_SZ = 1
    private const val REG_DWORD = 4
    private const val REG_MULTI_SZ = 7
    private const val ERROR_FILE_NOT_FOUND = 2

    sealed interface RegValue {
        class Text(val value: String) : RegValue
        class Texts(val values: List<String>) : RegValue
        class Number(val value: Int) : RegValue
    }

    fun writeMachineKey(path: String, values: Map<String, RegValue>): Boolean {
        val handle = PointerByReference()
        if (advapi32.RegCreateKeyExW(HKEY_LOCAL_MACHINE, WString(path), 0, null, 0, KEY_ALL_ACCESS_64, null, handle, null) != 0) return false
        val key = handle.value
        return try {
            values.all { (name, value) ->
                val (type, data) = when (value) {
                    is RegValue.Text -> REG_SZ to utf16(value.value + "\u0000")
                    is RegValue.Texts -> REG_MULTI_SZ to utf16(value.values.joinToString("") { it + "\u0000" } + "\u0000")
                    is RegValue.Number -> REG_DWORD to byteArrayOf(
                        value.value.toByte(), (value.value shr 8).toByte(), (value.value shr 16).toByte(), (value.value shr 24).toByte(),
                    )
                }
                advapi32.RegSetValueExW(key, WString(name), 0, type, data, data.size) == 0
            }
        } finally {
            advapi32.RegCloseKey(key)
        }
    }

    fun deleteMachineKey(path: String): Boolean =
        advapi32.RegDeleteTreeW(HKEY_LOCAL_MACHINE, WString(path)).let { it == 0 || it == ERROR_FILE_NOT_FOUND }

    private fun utf16(text: String): ByteArray = text.toByteArray(Charsets.UTF_16LE)

    class Route(val nextHop: String, val interfaceIndex: Int)

    fun bestRoute(ipv4: String): Route? {
        val octets = ipv4.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 && it.all { o -> o in 0..255 } } ?: return null
        val destination = octets[0] or (octets[1] shl 8) or (octets[2] shl 16) or (octets[3] shl 24)
        val row = Memory(56).apply { clear() }
        if (ipHelper.GetBestRoute(destination, 0, row) != 0) return null
        val hop = (0 until 4).joinToString(".") { (row.getByte(12L + it).toInt() and 0xff).toString() }
        return Route(hop, row.getInt(16))
    }

    fun runElevated(file: String, parameters: String, directory: String?): Pointer? {
        val info = ShellExecuteInfo().apply {
            fMask = SEE_MASK_NOCLOSEPROCESS or SEE_MASK_NOASYNC
            lpVerb = WString("runas")
            lpFile = WString(file)
            lpParameters = WString(parameters)
            lpDirectory = directory?.let(::WString)
            nShow = SW_HIDE
        }
        info.cbSize = info.size()
        if (!shell32.ShellExecuteExW(info)) {
            val error = Native.getLastError()
            if (error == ERROR_CANCELLED) throw ElevationDeclined()
            throw IllegalStateException("Windows did not start the helper (error $error)")
        }
        return info.hProcess
    }

    fun bestInterfaceAlias(ipv4: ByteArray): String? {
        val address = Memory(16).apply {
            clear()
            setShort(0, 2)
            write(4, ipv4, 0, 4)
        }
        val index = IntByReference()
        if (ipHelper.GetBestInterfaceEx(address, index) != 0) return null
        val luid = LongByReference()
        if (ipHelper.ConvertInterfaceIndexToLuid(index.value, luid) != 0) return null
        val alias = CharArray(257)
        if (ipHelper.ConvertInterfaceLuidToAlias(luid, alias, alias.size.toLong()) != 0) return null
        return String(alias).substringBefore('\u0000').takeIf { it.isNotBlank() }
    }

    fun sendCtrlC(processId: Long, process: Process?, waitMillis: Long): Boolean {
        val k = kernel32
        k.FreeConsole()
        if (!k.AttachConsole(processId.toInt())) return false
        k.SetConsoleCtrlHandler(null, true)
        return try {
            val sent = k.GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0)
            if (sent) process?.waitFor(waitMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            sent
        } finally {
            k.FreeConsole()
            k.SetConsoleCtrlHandler(null, false)
        }
    }

    class ElevationDeclined : Exception("administrator rights were not granted")
}
