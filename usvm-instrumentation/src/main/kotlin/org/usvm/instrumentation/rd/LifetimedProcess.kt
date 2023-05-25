import com.jetbrains.rd.util.lifetime.Lifetime

interface LifetimedProcess {
    val lifetime: Lifetime
    val process: Process
    fun terminate()
}

