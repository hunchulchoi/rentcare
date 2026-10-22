package com.rentcare.car

import android.app.Application
import com.rentcare.car.data.ApiClient
import com.rentcare.car.data.RentalStore
import com.rentcare.car.pleos.DrivingGateway
import com.rentcare.car.pleos.NaviGateway
import com.rentcare.car.pleos.VehicleGateway

class RentcareApp : Application() {
    lateinit var vehicle: VehicleGateway
        private set
    lateinit var navi: NaviGateway
        private set
    lateinit var driving: DrivingGateway
        private set
    lateinit var store: RentalStore
        private set
    lateinit var api: ApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        vehicle = VehicleGateway(this)
        navi = NaviGateway(this)
        driving = DrivingGateway(this)
        store = RentalStore(this)
        api = ApiClient(BuildConfig.API_BASE_URL.trimEnd('/'), BuildConfig.CAR_API_KEY)
    }
}
