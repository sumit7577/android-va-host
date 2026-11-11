package dev.bello.container.host

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/** Walk up the class hierarchy to find a field by name. */
fun findField(start: Class<*>, name: String): Field? {
    var c: Class<*>? = start
    while (c != null) {
        try {
            return c.getDeclaredField(name).apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            c = c.superclass
        }
    }
    return null
}

fun setField(target: Any, name: String, value: Any?): Boolean {
    val f = findField(target.javaClass, name) ?: return false
    return try { f.set(target, value); true } catch (_: Throwable) { false }
}

/** Copy every non-static declared field of [type] from one instance to another. */
fun copyFields(type: Class<*>, from: Any, to: Any) {
    for (f in type.declaredFields) {
        if (Modifier.isStatic(f.modifiers)) continue
        try {
            f.isAccessible = true
            f.set(to, f.get(from))
        } catch (_: Throwable) { /* best effort */ }
    }
}
