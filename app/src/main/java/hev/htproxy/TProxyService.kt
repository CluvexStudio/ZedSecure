package hev.htproxy

object TProxyService {
    @Suppress("FunctionName")
    external fun TProxyStartService(configPath: String, fd: Int): Boolean

    @Suppress("FunctionName")
    external fun TProxyStopService(): Boolean

    @Suppress("FunctionName")
    external fun TProxyIsRunning(): Boolean

    @Suppress("FunctionName")
    external fun TProxyGetStats(): LongArray?

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }
}
