package space.ourmosaic.app.utils

object Logger {
    fun d(tag: String, message: String) {
        println("D/$tag: $message")
    }

    fun w(tag: String, message: String) {
        println("W/$tag: $message")
    }

    fun e(tag: String, message: String, throwable: Throwable?) {
        println("E/$tag: $message")
        throwable?.printStackTrace()
    }
}
