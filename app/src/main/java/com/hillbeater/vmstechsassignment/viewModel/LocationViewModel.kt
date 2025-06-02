package com.hillbeater.vmstechsassignment.viewModel

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.hillbeater.vmstechsassignment.work.LocationWorker

class LocationViewModel : ViewModel() {

    val locationLiveData: LiveData<Location> = LocationWorker.locationLiveData
}
