// IGNORE_REVERSED_RESOLVE
// !DIAGNOSTICS: -UNUSED_VARIABLE
// FILE: A.java
public class A {
    public static int foo() {return 1;}
}

// FILE: 1.kt

fun foo() = ""

class B: A() {
    init {
        val a: Int = foo()
    }
}

// FILE: 2.kt
fun test() {
    fun foo() = ""

    class B: A() {
        init {
            val a: Int = <!INITIALIZER_TYPE_MISMATCH, TYPE_MISMATCH!>foo()<!> // todo
        }
    }
}
