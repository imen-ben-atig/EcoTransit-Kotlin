package com.example.mmmmmmmmmmm

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.widget.SearchView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mmmmmmmmmmm.Connect.RetrofitInstance
import com.example.mmmmmmmmmmm.Model.Location
import com.example.mmmmmmmmmmm.Repository.ApiService
import com.example.mmmmmmmmmmm.ViewModel.Retrofit
import com.example.mmmmmmmmmmm.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.Manifest
import android.widget.Toast
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
//floating location bottom
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineResult
import java.lang.ref.WeakReference
//end floating bottom


var mapView: MapView? = null
lateinit var binding: ActivityMainBinding

private var pointAnnotationManager: PointAnnotationManager? = null
class MainActivity2 : AppCompatActivity() {
    private val apiService = RetrofitInstance.retrofit.create(ApiService::class.java)
    private var allLocations: List<Location> = listOf() // Store all locations here


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapView = findViewById(R.id.mapView)
        mapView?.getMapboxMap()?.loadStyleUri(Style.OUTDOORS, object : Style.OnStyleLoaded {
            override fun onStyleLoaded(style: Style) {
                enableLocationComponent(style)
            }
        })

        val call: Call<List<Location>> = apiService.getPost()
        call.enqueue(object : Callback<List<Location>> {
            override fun onResponse(call: Call<List<Location>>, response: Response<List<Location>>) {
                if (response.isSuccessful) {
                    response.body()?.let {locations->
                        allLocations = locations // Store the locations
                        // Update the UI on the main thread
                        runOnUiThread {
                            //addAnnotationToMap(location[0].lat, location[0].long) // add annotation when the style map is loaded
                            for (loc in locations) {
                                addAnnotationToMap(loc.lat, loc.long)
                                Log.d("bilel", "Error: ${locations}")
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                    }
                }
            }
            override fun onFailure(call: Call<List<Location>>, t: Throwable) {
                // Handle network failures
                // For example, you can show an error message in the TextView
                runOnUiThread {
                    Log.d("bilel", "Error: ${t.message}")
                }
            }
        })
        fetchAndLogLocations()
        //search view
        val searchView: SearchView = findViewById(R.id.search_view)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchForAnnotation(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
// End search view
        val fabCurrentLocation: FloatingActionButton = findViewById(R.id.currentlocation)
        fabCurrentLocation.setOnClickListener {
            //goToCurrentLocation()
        }
    }
    private fun searchForAnnotation(query: String) {
        // Filter for locations that match the query by name or category
        val matchedLocations = allLocations.filter {
            it.name.equals(query, ignoreCase = true) ||
                    it.categorie.equals(query, ignoreCase = true)
        }

        if (matchedLocations.isNotEmpty()) {
            // Assuming you want to move to the first matched location
            val firstMatch = matchedLocations.first()
            val cameraPosition = CameraOptions.Builder()
                .center(Point.fromLngLat(firstMatch.lat, firstMatch.long))
                .zoom(15.0)  // Adjust the zoom level as needed
                .build()
            mapView?.getMapboxMap()?.flyTo(cameraPosition)
        } else {
            Toast.makeText(this, "No matching location found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchForLocation(query: String) {
        // Filter the allLocations list based on the query
        val filteredLocations = allLocations.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.categorie.contains(query, ignoreCase = true)
        }

        // Clear existing annotations
        pointAnnotationManager?.deleteAll()

        // Add new annotations to the map for filtered locations
        for (location in filteredLocations) {
            addAnnotationToMap(location.lat, location.long)
        }
    }


    private fun addAnnotationToMap(latitude: Double, longitude: Double) {
        bitmapFromDrawableRes(this@MainActivity2, R.drawable.ic_banner_foreground)?.let { bitmap ->
            val annotationApi = mapView?.annotations
            val pointAnnotationManager = annotationApi?.createPointAnnotationManager(mapView!!)

            // Add custom annotation
            addCustomAnnotation(pointAnnotationManager, R.drawable.leau, latitude, longitude)

            // Set up the click listener for annotations
            pointAnnotationManager?.addClickListener { pointAnnotation ->
                showBottomSheetDialog(pointAnnotation)
                true // Return true to indicate that the click event is consumed.
            }
        }
    }
        private fun showBottomSheetDialog(pointAnnotation: PointAnnotation) {
            val bottomSheetDialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottomsheet, null)
            bottomSheetDialog.setContentView(view)
            val nameTextView = view.findViewById<TextView>(R.id.name)
            val adresseTextView = view.findViewById<TextView>(R.id.adresse)
            val categorie = view.findViewById<TextView>(R.id.categorie)
            // Add other TextViews or views for additional details
            Log.d("aaa", "Clicked at: Lat=${pointAnnotation.point.latitude()}, Long=${pointAnnotation.point.longitude()}")

            // Find the location that matches the annotation's coordinates
            val matchingLocation = allLocations.firstOrNull { location ->
                location.lat == pointAnnotation.point.longitude()
                //  Log.d("bbb", "Location from server: Name=${location.name} Lat=${location.lat}, Long=${location.long}")
            }
            if (matchingLocation != null) {
                // Set the text of TextViews to show the details of the matching location
                nameTextView.text = matchingLocation.name
                adresseTextView.text = matchingLocation.adresse
                categorie.text = matchingLocation.categorie
                // Set other details similarly...
            } else {
                // No matching location found
                nameTextView.text = "No matching location found"
                adresseTextView.text = ""
                // Clear or set default text for other details...
            }
            bottomSheetDialog.show()

        }

    private fun addCustomAnnotation(
        pointAnnotationManager: PointAnnotationManager?,
        @DrawableRes imageRes: Int,
        longitude: Double,
        latitude: Double
    ) {
        bitmapFromDrawableRes(this@MainActivity2, imageRes)?.let {
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                .withPoint(com.mapbox.geojson.Point.fromLngLat(longitude, latitude))
                .withIconImage(it)

            pointAnnotationManager?.create(pointAnnotationOptions)
        }
    }
    private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) =
        convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId))


private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
    if (sourceDrawable == null) {
        return null
    }
    return if (sourceDrawable is BitmapDrawable) {
        sourceDrawable.bitmap
    } else {
// copying drawable object to not manipulate on the same reference
        val constantState = sourceDrawable.constantState ?: return null
        val drawable = constantState.newDrawable().mutate()
        val bitmap: Bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth, drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    }
}
    fun fetchAndLogLocations() {
        val apiService = RetrofitInstance.retrofit.create(ApiService::class.java)

        apiService.getPost().enqueue(object : Callback<List<Location>> {
            override fun onResponse(call: Call<List<Location>>, response: Response<List<Location>>) {
                if (response.isSuccessful) {
                    response.body()?.forEach { location ->
                        Log.d("zabb", "Name: ${location.name}, Lat: ${location.lat}, Long: ${location.long}, Address: ${location.adresse}, Category: ${location.categorie}, Instruction: ${location.instruction}")
                    }
                } else {
                    Log.e("zabb", "Response not successful")
                }
            }

            override fun onFailure(call: Call<List<Location>>, t: Throwable) {
                Log.e("zabb", "Network request failed: ${t.message}")
            }
        })
    }
    // location acces
    private fun enableLocationComponent(loadedMapStyle: Style) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mapView?.location?.updateSettings {
                enabled = true
                pulsingEnabled = true
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSIONS_REQUEST_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mapView?.getMapboxMap()?.getStyle { style ->
                    enableLocationComponent(style)
                }
            } else {
                Toast.makeText(this, "User location permission not granted", Toast.LENGTH_LONG).show()
            }
        }
    }
    companion object {
        private const val PERMISSIONS_REQUEST_LOCATION = 101
    }
    // END location acces

}