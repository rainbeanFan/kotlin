/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package com.example

import com.example.exported

fun main() {
    val exp = exported()
    println("foo.main: exp = $exp")
}