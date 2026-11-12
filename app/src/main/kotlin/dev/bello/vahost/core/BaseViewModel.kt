package dev.bello.vahost.core

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface ViewEvent
interface ViewState

/**
 * Local stand-in for the KMP app's BaseViewModel. Same contract --
 * setInitialState / onTriggerEvent / setState { copy(...) } -- without pulling
 * in the DI and networking layers that app's version depends on.
 */
abstract class BaseViewModel<Event : ViewEvent, State : ViewState> : ViewModel() {

    private val _state = MutableStateFlow(setInitialState())
    val state: StateFlow<State> = _state.asStateFlow()

    abstract fun setInitialState(): State

    abstract fun onTriggerEvent(event: Event)

    protected fun setState(reducer: State.() -> State) = _state.update(reducer)
}
