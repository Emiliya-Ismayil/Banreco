package com.illegaldisease.banreco.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.net.ConnectivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.provider.CalendarContract
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.*
import android.widget.Toast

import co.zsmb.materialdrawerkt.builders.accountHeader
import co.zsmb.materialdrawerkt.builders.drawer
import co.zsmb.materialdrawerkt.draweritems.badgeable.secondaryItem
import co.zsmb.materialdrawerkt.draweritems.divider
import co.zsmb.materialdrawerkt.draweritems.profile.profile

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.vision.text.TextBlock
import com.google.android.gms.vision.text.TextRecognizer
import com.illegaldisease.banreco.OcrDetectorProcessor
import com.illegaldisease.banreco.OcrGraphic
import com.illegaldisease.banreco.R
import com.illegaldisease.banreco.camera.CameraSource
import com.illegaldisease.banreco.camera.CameraSourcePreview
import com.illegaldisease.banreco.camera.GraphicOverlay
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog
import java.io.IOException

import java.util.*

class CameraActivity : AppCompatActivity(), TimePickerDialog.OnTimeSetListener,DatePickerDialog.OnDateSetListener {
    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "OcrCaptureActivity"

        // Intent request code to handle updating play services if needed.
        private const val RC_HANDLE_GMS = 9001

        // Permission request codes need to be < 256
        private const val RC_HANDLE_CAMERA_PERM = 2

        // Constants used to pass extra data in the intent
        const val AutoFocus = "AutoFocus"
        const val UseFlash = "UseFlash"
        const val TextBlockObject = "String"
    }
    private var mCameraSource: CameraSource? = null
    private var mPreview: CameraSourcePreview? = null
    private var mGraphicOverlay: GraphicOverlay<OcrGraphic>? = null

    // Helper objects for detecting taps and pinches.
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var gestureDetector: GestureDetector? = null

    private var mGoogleSignInClient : GoogleSignInClient? = null
    private var signInAccount : GoogleSignInAccount? = null
    private var profilePic : Bitmap? = null
    private var profileMail : String? = null
    private var profileName : String? = null
    private var lastEventDate : Calendar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_camera)

        profilePic = BitmapFactory.decodeResource(this@CameraActivity.resources, R.drawable.photo1)
        profileMail = "notsignedin@placeholder.com" //Placeholder values
        profileName = "Anonymouse" //I know it is anonymous, it is intended.
        lastEventDate = GregorianCalendar.getInstance(TimeZone.getDefault()) //Don't forget to re-initialize

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestId()
                .requestProfile()
                .build()
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        buildThings()
        //initializeDrawerBar() //Commented out until i find a way to update drawer runtime
    }
    override fun onStart() {
        super.onStart()
        checkSignIn() // Attempts to login with async callbacks. be careful.
    }
    private fun initializeDrawerBar(){
        drawer {
            accountHeader{
                background = R.drawable.images
                profile(profileName!!,profileMail!!){
                    //According to google, photoUrl will be null if user does not have Google+ enabled and have profile there. So i will add placeholder for now.
                    iconBitmap = profilePic!! //Fallback is described at oncreate
                    //TODO: Consider adding sign-out options ??
                }
            }
            secondaryItem(getString(R.string.drawer_events)) {
                icon = R.drawable.ic_event_available_black_24dp
                onClick {_ ->
                    startActivity(Intent(this@CameraActivity, EventsActivity:: class.java))
                    false
                }
            }
            secondaryItem(getString(R.string.drawer_calendar)) {
                icon = R.drawable.ic_date_range_black_24dp
                onClick {_ ->
                    openCalendar(System.currentTimeMillis())
                    false
                }
            }
            secondaryItem(getString(R.string.drawer_settings)) {
                icon = R.drawable.ic_settings_black_24dp
                onClick {_ ->
                    startActivity(Intent(this@CameraActivity, SettingsActivity:: class.java))
                    false
                }
            }
            secondaryItem(getString(R.string.drawer_logs)) {
                icon = R.drawable.ic_archive_black_24dp
                onClick {_ ->
                    startActivity(Intent(this@CameraActivity, LogsActivity:: class.java))
                    false
                }
            }
            secondaryItem(getString(R.string.drawer_about)) {
                icon = R.drawable.ic_more_black_24dp
                onClick {_ ->
                    //I don't care, leave it empty
                    false
                }
            }
            secondaryItem(getString(R.string.drawer_rate)) {
                icon = R.drawable.ic_star_border_black_24dp
                onClick { _ ->
                    //Fill here if you put this on google play
                    false
                }
            }
            divider {  }
            secondaryItem(getString(R.string.drawer_logout)) {
                icon = R.drawable.ic_exit_to_app_black_24dp
                onClick {_ ->
                    finish()
                    System.exit(0) //Exit with success
                    false
                }
            }
        }
        mPreview!!.stop() //There is a bug going on. When you draw the material bar, camera goes black.
        startCameraSource() //So i am redrawing camera.
    }
    private fun openCalendar(openTime : Long){
        val builder = CalendarContract.CONTENT_URI.buildUpon()
        builder.appendPath("time")
        ContentUris.appendId(builder, openTime)
        val intent = Intent(Intent.ACTION_VIEW).setData(builder.build())
        startActivity(intent)
    }
    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting
    }
    private fun checkSignIn(){
        if(!isOnline()){
            //Means we are not connected to internet. Prompt user and leave if necessary
            //TODO: Add dialog here.
        }
        val task = mGoogleSignInClient!!.silentSignIn()
        if (task.isSuccessful) {
            // There's immediate result available.
            signInAccount = task.result
            postSignIn()
        } else {
            // There's no immediate result ready, displays some progress indicator and waits for the
            // async callback.
            //TODO: I don't know, maybe show some animation when waiting to log in ?
            task.addOnCompleteListener(this){ signIn ->
                if(signIn.isSuccessful){
                    signInAccount = task.result
                    postSignIn()
                }
                else{
                    //It is failed. Send some errors or i don't know. Maybe try signing in again  ?
                    signInToGoogle() //Result is async.
                }
            }
        }
    }
    private fun postSignIn() {
        val currentUri = signInAccount!!.photoUrl
        profileName = signInAccount!!.displayName
        profileMail = signInAccount!!.email
        val target = object : SimpleTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap?, transition: Transition<in Bitmap>?) {
                profilePic = resource
                initializeDrawerBar()
            }
            override fun onLoadFailed(errorDrawable: Drawable?) {
                //It is not guaranteed that we can get our url. Fallback is provided in drawerbar.
                super.onLoadFailed(errorDrawable)
                initializeDrawerBar()
            }
        }
        Glide.with(this)
                .asBitmap()
                .load(currentUri)
                .into(target)
    }
    private fun signInToGoogle(){
        val signInIntent = mGoogleSignInClient!!.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN) //We checked internet connection before
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == RC_SIGN_IN){
            if(resultCode == Activity.RESULT_OK){
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                signInAccount = task.result //This is newly signed in user.
                postSignIn()
            }
            else{
                //TODO: Sign in is cancelled, do something else ?
                initializeDrawerBar() // Initialize it with placeholders. Program will probably cease to work at other steps.
            }
        }
    }
    private fun pickTime(){
        val tpd = TimePickerDialog.newInstance(
                this@CameraActivity,
                true //TODO: You might consider time modes
        )
        tpd.show(fragmentManager,"TimePicker")
        tpd.version = TimePickerDialog.Version.VERSION_2
    }
    private fun pickDate(){
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog.newInstance(
                this@CameraActivity,
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        )
        dpd.show(fragmentManager, "DatePicker")
        dpd.version = DatePickerDialog.Version.VERSION_2
    }
    override fun onDateSet(view: DatePickerDialog?, year: Int, monthOfYear: Int, dayOfMonth: Int) {
        lastEventDate!!.set(Calendar.MONTH, monthOfYear)
        lastEventDate!!.set(Calendar.YEAR, year)
        lastEventDate!!.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        pickTime()
    }
    override fun onTimeSet(view: TimePickerDialog?, hourOfDay: Int, minute: Int, second: Int) {
        lastEventDate!!.set(Calendar.HOUR_OF_DAY, hourOfDay)
        lastEventDate!!.set(Calendar.MINUTE, minute)
        lastEventDate!!.set(Calendar.SECOND, second)
    }
    private fun buildThings(){
        mPreview = findViewById<CameraSourcePreview>(R.id.preview)
        mGraphicOverlay = findViewById(R.id.graphicOverlay) as GraphicOverlay<OcrGraphic>

        // read parameters from the intent used to launch the activity.
        val autoFocus = intent.getBooleanExtra(AutoFocus, false)
        val useFlash = intent.getBooleanExtra(UseFlash, false)

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        val rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(autoFocus, useFlash)
        } else {
            requestCameraPermission()
        }
        gestureDetector = GestureDetector(this, CaptureGestureListener())
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

    }

    private fun requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission")

        val permissions = arrayOf(Manifest.permission.CAMERA)

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM)
            return
        }

        val listener = View.OnClickListener {
            ActivityCompat.requestPermissions(this, permissions,
                    RC_HANDLE_CAMERA_PERM)
        }

        Snackbar.make(mGraphicOverlay!!, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val b = scaleGestureDetector!!.onTouchEvent(e)

        val c = gestureDetector!!.onTouchEvent(e)

        return b || c || this.onTouchEvent(e)
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the ocr detector to detect small text samples
     * at long distances.
     *
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private fun createCameraSource(autoFocus: Boolean, useFlash: Boolean) {

        // A text recognizer is created to find text.  An associated processor instance
        // is set to receive the text recognition results and display graphics for each text block
        // on screen.
        val textRecognizer = TextRecognizer.Builder(this).build()
        textRecognizer.setProcessor(OcrDetectorProcessor(mGraphicOverlay))

        if (!textRecognizer.isOperational) {
            // Note: The first time that an app using a Vision API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any text,
            // barcodes, or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.")

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            val lowstorageFilter = IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW)
            val hasLowStorage = this.registerReceiver(null, lowstorageFilter) != null

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show()
                Log.w(TAG, getString(R.string.low_storage_error))
            }
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the text recognizer to detect small pieces of text.
        mCameraSource = CameraSource.Builder(this, textRecognizer)
                .setFacing(CameraSource.CAMERA_FACING_BACK) //TODO: Change this
                .setRequestedPreviewSize(1280, 1024)
                .setRequestedFps(2.0f)
                .build()
    }

    /**
     * Restarts the camera.
     */
    override fun onResume() {
        super.onResume()
        startCameraSource()
    }

    /**
     * Stops the camera.
     */
    override fun onPause() {
        super.onPause()
        if (mPreview != null) {
            mPreview!!.stop()
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (mPreview != null) {
            mPreview!!.release()
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on [.requestPermissions].
     *
     *
     * **Note:** It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     *
     *
     * @param requestCode  The request code passed in [.requestPermissions].
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     * which is either [PackageManager.PERMISSION_GRANTED]
     * or [PackageManager.PERMISSION_DENIED]. Never null.
     * @see .requestPermissions
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: $requestCode")
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source")
            // We have permission, so create the camerasource
            val autoFocus = this.intent.getBooleanExtra(AutoFocus, false)
            val useFlash = this.intent.getBooleanExtra(UseFlash, false)
            createCameraSource(autoFocus, useFlash)
            return
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.size +
                " Result code = " + if (grantResults.isNotEmpty()) grantResults[0] else "(empty)")

        val listener = DialogInterface.OnClickListener { dialog, id -> finish() }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Multitracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show()
    }
    @SuppressLint("MissingPermission") //TODO: If something bad occurs, look here first
    private fun startCameraSource() {
        // Check that the device has play services available.
        var code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                applicationContext)
        if (code != ConnectionResult.SUCCESS) {
            var dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS)
            dlg.show()
        }

        if (mCameraSource != null) {
            try {
                mPreview!!.start(mCameraSource, mGraphicOverlay)
            } catch (e : IOException) {
                Log.e(TAG, "Unable to start camera source.", e)
                mCameraSource!!.release()
                mCameraSource = null
            }
        }
    }
    /**
     * onTap is called to capture the first TextBlock under the tap location and return it to
     * the Initializing Activity.
     *
     * @param rawX - the raw position of the tap
     * @param rawY - the raw position of the tap.
     * @return true if the activity is ending.
     */
    private fun onTap(rawX : Float ,rawY : Float) : Boolean {
        var graphic = mGraphicOverlay!!.getGraphicAtLocation(rawX, rawY)
        var text : TextBlock? = null
        if (graphic != null) {
            text = graphic.textBlock
            if (text != null && text.value != null) {
                var data = Intent()
                data.putExtra(TextBlockObject, text.value)
                setResult(CommonStatusCodes.SUCCESS, data)
                finish()
            }
            else {
                Log.d(TAG, "text data is null")
            }
        }
        else {
            Log.d(TAG,"no text detected")
        }
        return text != null
    }

    private inner class CaptureGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return onTap(e.rawX, e.rawY) || super.onSingleTapConfirmed(e)
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.OnScaleGestureListener {

        /**
         * Responds to scaling events for a gesture in progress.
         * Reported by pointer motion.
         *
         * @param detector The detector reporting the event - use this to
         * retrieve extended info about event state.
         * @return Whether or not the detector should consider this event
         * as handled. If an event was not handled, the detector
         * will continue to accumulate movement until an event is
         * handled. This can be useful if an application, for example,
         * only wants to update scaling factors if the change is
         * greater than 0.01.
         */
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            return false
        }

        /**
         * Responds to the beginning of a scaling gesture. Reported by
         * new pointers going down.
         *
         * @param detector The detector reporting the event - use this to
         * retrieve extended info about event state.
         * @return Whether or not the detector should continue recognizing
         * this gesture. For example, if a gesture is beginning
         * with a focal point outside of a region where it makes
         * sense, onScaleBegin() may return false to ignore the
         * rest of the gesture.
         */
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            return true
        }

        /**
         * Responds to the end of a scale gesture. Reported by existing
         * pointers going up.
         *
         *
         * Once a scale has ended, [ScaleGestureDetector.getFocusX]
         * and [ScaleGestureDetector.getFocusY] will return focal point
         * of the pointers remaining on the screen.
         *
         * @param detector The detector reporting the event - use this to
         * retrieve extended info about event state.
         */
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            mCameraSource!!.doZoom(detector.scaleFactor)
        }
    }
}
