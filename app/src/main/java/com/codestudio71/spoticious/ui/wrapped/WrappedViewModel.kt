package com.codestudio71.spoticious.ui.wrapped

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codestudio71.spoticious.data.wrapped.ArtistPlayCount
import com.codestudio71.spoticious.data.wrapped.SpoticiousDatabase
import com.codestudio71.spoticious.data.wrapped.TitlePlayCount
import com.codestudio71.spoticious.data.wrapped.WrappedPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WrappedViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = SpoticiousDatabase.get(application).playEventDao()

    private val _period = MutableStateFlow(WrappedPeriod.ALL_TIME)
    val period: StateFlow<WrappedPeriod> = _period.asStateFlow()

    private val _topTracks = MutableStateFlow<List<TitlePlayCount>>(emptyList())
    val topTracks: StateFlow<List<TitlePlayCount>> = _topTracks.asStateFlow()

    private val _topArtists = MutableStateFlow<List<ArtistPlayCount>>(emptyList())
    val topArtists: StateFlow<List<ArtistPlayCount>> = _topArtists.asStateFlow()

    private val _totalPlays = MutableStateFlow(0)
    val totalPlays: StateFlow<Int> = _totalPlays.asStateFlow()

    private val _totalTimeMs = MutableStateFlow(0L)
    val totalTimeMs: StateFlow<Long> = _totalTimeMs.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun setPeriod(period: WrappedPeriod) {
        if (_period.value == period) return
        _period.value = period
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val since = _period.value.sinceMs()
            withContext(Dispatchers.IO) {
                _topTracks.value = dao.topTracks(since)
                _topArtists.value = dao.topArtists(since)
                _totalPlays.value = dao.totalPlays(since)
                _totalTimeMs.value = dao.totalListenedTimeMs(since)
            }
            _loading.value = false
        }
    }
}
