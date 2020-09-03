package hu.prooktatas.djs.fragment

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioGroup
import androidx.core.content.FileProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import hu.prooktatas.djs.BuildConfig
import hu.prooktatas.djs.R
import hu.prooktatas.djs.TAG
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*


class PhotoFragment : Fragment() {

    private lateinit var fab: FloatingActionButton

    private val requestCodePickExistingImage = 101
    private val requestCodeTakePictureBitmap = 102
    private val requestCodeTakePictureAndSaveImage = 103

    private var bitmap: Bitmap? = null
    private lateinit var imageView: ImageView
    private lateinit var radioButtonGroup: RadioGroup

    private lateinit var currentPhotoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_photo, container, false)

        imageView = rootView.findViewById(R.id.ivUserPhoto)
        radioButtonGroup = rootView.findViewById(R.id.rgPhotoOptions)

        fab = rootView.findViewById(R.id.fab)
        fab.setOnClickListener {
            Log.d(TAG, "FAB clicked. Radio group selection: ${radioButtonGroup.checkedRadioButtonId}")

            when(radioButtonGroup.checkedRadioButtonId) {
                R.id.rbExisting -> pickAnExistingImage()
                else -> takePictureAndSave() ///takePictureAndGetThumbnail()
            }
        }

        return rootView
    }

    companion object {

        @JvmStatic
        fun newInstance() = PhotoFragment()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult() called...")

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                requestCodePickExistingImage -> {
                    var inputStream: InputStream? = null
                    try {
                        bitmap?.recycle()

                        data?.data?.let {
                            inputStream = activity!!.contentResolver.openInputStream(it)
                            bitmap = BitmapFactory.decodeStream(inputStream)
                            imageView.setImageBitmap(bitmap)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, e.localizedMessage)
                    } finally {
                        inputStream?.close()
                    }
                }


                requestCodeTakePictureBitmap -> {
                    // Ebben az esetben az Intent.extras-ban benne lesz egy Bitmap
                    val imageBitmap = data?.extras?.get("data") as Bitmap
                    imageView.setImageBitmap(imageBitmap)
                }

                requestCodeTakePictureAndSaveImage -> {
                    loadImageFromFile()
                }
            }
        }
    }

    /**
     * A meglevo kepek kozul enged egyet valasztani
     */
    private fun pickAnExistingImage() {
        Log.d(TAG, "launchCamera() called...")
        val intent =  Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, requestCodePickExistingImage)
    }

    /**
     * Itt ugy hivjuk meg az implicit intent-et, hogy nem adunk at eleresi utat, azaz nem varjuk el tole, hogy mentse a kepet a hattertarra.
     * Ilyenkor a kep Bitmap peldanykent fog visszaterni az onActivityResult-ban
     */
    private fun takePictureAndGetThumbnail() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(takePictureIntent, requestCodeTakePictureBitmap)

    }

    /**
     * Egy temp fajlt hoz letre adott nevkonvencioval az alkalmazas sajat sandboxaban.
     * Ez itt meg ures fajl lesz. Kesobb ebbe kerul bele az elkeszitett fenykep binaris tartalma.
     */
    private fun createImageFile(): File {
        // Kepzunk valami file nev konvenciot
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = activity!!.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            // Az absolutPath a File osztaly property-je!
            // Elmenthetjuk a kep elersi utjat, amit aztan hasznalhatunk ACTION_VIEW tipusu Intent-ben
            currentPhotoPath = absolutePath
        }
    }

    /**
     * A fentihez hasonloan itt is ugyanazt az implicit intent-et hivjuk meg, de atadunk egy extra parametert, egy Uri-t, amivel
     * jelezzunk az elinditando Activity szamara, hogy az elkeszitett fotot mentse a hattertarra, az Uri-val reprezentalt helyre.
     */
    private fun takePictureAndSave() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Akkor fut le, ha letezik a rendszerben az ACTION_IMAGE_CAPTURE intent, azaz sikerult feloldani ez az implicit intentet.
            // Ennek tobbek kozott feltetele az is, hogy legyen kamera az eszkozon.
            takePictureIntent.resolveActivity(activity!!.packageManager)?.also {
                // Letrehozunk egy ures temp fajlt a kep szamara
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Ha nem sikerult letrehozni az ures fajlt, akkor a photoFile erteke null lesz
                    null
                }

                Log.d(TAG, "photoFile: $photoFile")

                // Csak akkor folytatjuk, ha letrejott az ures fajl
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        activity!!,
                        "hu.prooktatas.djs.fileprovider",
                        it
                    )

                    /*
                        Egy masik modszer arra, hogy egy File eleresi utjat URI-va konverteljuk:
                        val photoURI = FileProvider.getUriForFile(Objects.requireNonNull(activity!!), BuildConfig.APPLICATION_ID + ".fileprovider", it)
                     */

                    Log.d(TAG, "photoURI: $photoURI")

                    // Az ACTION_IMAGE_CAPTURE implicit intentnek at kell adni egy Uri-t, ami egy fajlt reprezental. Ebbe a fajlba fogja elmenteni a kamera
                    // kezelo rendszer a keszitett fotot.
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, requestCodeTakePictureAndSaveImage)
                }
            }
        }
    }

    /**
     * A currentPhotoPath-ban tarolt eleresi ut altal kijelolt fajlbol Bitmap-et keszit, majd betolti azt az ImageView-ba.
     */
    private fun loadImageFromFile() {
        Log.d(TAG, "loadImageFromFile() called...")
        Log.d(TAG, "currentPhotoPath: $currentPhotoPath")

        // FONTOS! Az android fajlrendszerben ide kerul a kep:
        // /storage/emulated/0/Android/data/hu.prooktatas.djs/files/Pictures/JPEG_20200628_125001_6949545523058870892.jpg
        // Ezt a Device file explorerben azonban itt talajuk: sdcard/Android/data/hu.prooktatas.djs/...

        // TODO: nehany kepkeszites utan nezzunk bele az app sandbox mappajaba ADB shell-bol

        BitmapFactory.decodeFile(currentPhotoPath, BitmapFactory.Options())?.also { bitmap ->
            imageView.setImageBitmap(bitmap)
        }

    }

}