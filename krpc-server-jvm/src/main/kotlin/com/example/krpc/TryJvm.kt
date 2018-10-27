package com.example.krpc

fun ErrorMapper.printingStackTraces(): ErrorMapper = { throwable: Throwable ->
    throwable.printStackTrace()
    this(throwable)
}
