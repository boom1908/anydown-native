package com.boom.anydown

sealed class AnydownState {
    object Idle : AnydownState()
    data class Loading(val progress: Float) : AnydownState()
    data class Results(val title: String, val channel: String, val duration: String, val thumbnailUrl: String = "") : AnydownState()
}
