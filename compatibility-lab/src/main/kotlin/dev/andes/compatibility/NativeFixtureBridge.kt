package dev.andes.compatibility

object NativeFixtureBridge {
    init { System.loadLibrary("runtime_boundary") }
    external fun attach(fixture: Class<*>): Boolean
}
