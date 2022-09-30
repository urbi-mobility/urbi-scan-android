package co.urbi.android.urbiscan.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.HandlerThread
import android.os.Vibrator
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import co.urbi.android.urbiscan.R
import co.urbi.android.urbiscan.databinding.ActivityScanNfcBinding
import co.urbi.android.urbiscan.utils.*
import kotlinx.coroutines.*
import net.sf.scuba.smartcards.CardFileInputStream
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardSecurityFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.*
import org.jmrtd.lds.iso19794.FaceImageInfo
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.security.Security
import java.util.*

class ScanNFCActivity : AppCompatActivity(), CoroutineScope by MainScope(), NfcAdapter.ReaderCallback {

    private lateinit var binding: ActivityScanNfcBinding
    private lateinit var mrzInfo: MRZInfo
    private var scanType = ScanUtils.SCAN_CARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanNfcBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        (intent.getSerializableExtra(ScanUtils.SCANNED_MRZ_INFO) as? MRZInfo)?.let {
            mrzInfo = it
        }
        (intent.getIntExtra(ScanUtils.SCAN_TYPE, ScanUtils.SCAN_CARD)).let {
            scanType = it
        }
        when (scanType) {
            ScanUtils.SCAN_CARD -> {
                binding.image.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_id_scan))
                binding.message.text = getString(R.string.scan_message_id)
            }
            ScanUtils.SCAN_PASSPORT -> {
                binding.image.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_passport_scan))
                binding.message.text = getString(R.string.scan_message_passport)
            }
        }
    }

    private fun checkNFC(): Boolean {
        val manager = getSystemService(Context.NFC_SERVICE) as? NfcManager
        return manager?.defaultAdapter?.isEnabled == true
    }

    private fun promptNFCSettings() {
        ScanUtils.createAlertDialog(
            context = this,
            title = getString(R.string.nfc_dialog_title),
            message = getString(R.string.nfc_dialog_message),
            positiveTitle = getString(R.string.nfc_dialog_positive),
            positiveListener = { dialog, which ->
                dialog.dismiss()
                startActivity(Intent("android.settings.NFC_SETTINGS"))
            },
            negativeTitle = getString(R.string.nfc_dialog_negative),
            negativeListener = { dialog, which ->
                dialog.dismiss()
                promptFailure()
            }
        ).show()
    }

    override fun onResume() {
        super.onResume()
        if (checkNFC()) {
            startScan()
        } else {
            promptNFCSettings()
        }
    }

    override fun onPause() {
        super.onPause()
        stopScan()
    }

    private fun startScan() {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter != null) {
            val options = Bundle()
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)

            adapter.enableReaderMode(
                this,
                this,
                NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or
                        NfcAdapter.FLAG_READER_NFC_V or
                        NfcAdapter.FLAG_READER_NFC_BARCODE or
                        NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                options
            )
        }
    }

    private fun stopScan() {
        NfcAdapter.getDefaultAdapter(this)?.disableForegroundDispatch(this)
    }

    fun vibrate(millis: Long = 50) {
        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(millis)
    }

    private suspend fun readData(isoDep: IsoDep, bacKey: BACKeySpec) = withContext(Dispatchers.IO) {
        try {
            var dg1File: DG1File? = null // Data group 1 contains the MRZ (name, surname, birth, expiry, docNumber, docType, gender, nationality)
            var dg2File: DG2File? = null // Data group 2 contains face image data (profile pic)
            var dg3File: DG3File? = null // Data group 3 contains finger print data
            var dg4File: DG4File? = null // Data group 4 contains iris data
            var dg5File: DG5File? = null // Data group 5 contains displayed portrait
            var dg6File: DG6File? = null // Data group 6 is RFU
            var dg7File: DG7File? = null // Data group 7 contains displayed signature
            var dg11File: DG11File? = null // Data group 11 contains additional personal details (personalNumber aka CF, permanentAddress, placeOfBirth, telephone)
            var dg12File: DG12File? = null // Data group 12 contains additional document details
            var dg14File: DG14File? = null // Data group 14 contains security infos
            var dg15File: DG15File? = null // Data group 15 contains the public key used for Active Authentication
            var sodFile: SODFile? = null // The security document
            var comFile: COMFile? = null // The data group presence list
            var imageBase64: String? = null
            var bitmap: Bitmap? = null

            val cardService = CardService.getInstance(isoDep)
            cardService.open()

            val service = PassportService(cardService, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH, PassportService.DEFAULT_MAX_BLOCKSIZE, true, false)
            service.open()
            var paceSucceeded = false

            try {
                val cardSecurityFile = CardSecurityFile(service.getInputStream(PassportService.EF_CARD_SECURITY) as CardFileInputStream)
                val securityInfos = cardSecurityFile.securityInfos
                securityInfos.forEach {
                    if (it is PACEInfo) {
                        service.doPACE(bacKey, it.getObjectIdentifier(), PACEInfo.toParameterSpec(it.parameterId), null)
                        paceSucceeded = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, e.toString())
            }
            service.sendSelectApplet(paceSucceeded)
            if (!paceSucceeded) {
                try {
                    service.getInputStream(PassportService.EF_COM).read()
                } catch (e: java.lang.Exception) {
                    service.doBAC(bacKey)
                }
            }

            try {
                val dg1In = service.getInputStream(PassportService.EF_DG1) as CardFileInputStream
                dg1File = DG1File(dg1In)
                withContext(Dispatchers.Main) { binding.dot1.isEnabled = false }
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
                withContext(Dispatchers.Main) { binding.dot1.isEnabled = false }
            }
            try {
                val dg2In = service.getInputStream(PassportService.EF_DG2) as CardFileInputStream
                dg2File = DG2File(dg2In)
                withContext(Dispatchers.Main) { binding.dot2.isEnabled = false }
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
                withContext(Dispatchers.Main) { binding.dot2.isEnabled = false }
            }
            try {
                val dg3In = service.getInputStream(PassportService.EF_DG3) as CardFileInputStream
                dg3File = DG3File(dg3In)
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
            }
            try {
                val dg4In = service.getInputStream(PassportService.EF_DG4) as CardFileInputStream
                dg4File = DG4File(dg4In)
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
            }
            try {
                val dg5In = service.getInputStream(PassportService.EF_DG5) as CardFileInputStream
                dg5File = DG5File(dg5In)
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
            }
            try {
                val dg6In = service.getInputStream(PassportService.EF_DG6) as CardFileInputStream
                dg6File = DG6File(dg6In)
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
            }
            try {
                val dg7In = service.getInputStream(PassportService.EF_DG7) as CardFileInputStream
                dg7File = DG7File(dg7In)
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
            }
            try {
                val dg11In = service.getInputStream(PassportService.EF_DG11) as CardFileInputStream
                dg11File = DG11File(dg11In)
                withContext(Dispatchers.Main) { binding.dot3.isEnabled = false }
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
                withContext(Dispatchers.Main) { binding.dot3.isEnabled = false }
            }
            try {
                val dg12In = service.getInputStream(PassportService.EF_DG12) as CardFileInputStream
                dg12File = DG12File(dg12In)
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
            }
            try {
                val dg14In = service.getInputStream(PassportService.EF_DG14) as CardFileInputStream
                dg14File = DG14File(dg14In)
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
            }
            try {
                val dg15In = service.getInputStream(PassportService.EF_DG15) as CardFileInputStream
                dg15File = DG15File(dg15In)
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
            }
            try {
                val sodIn = service.getInputStream(PassportService.EF_SOD) as CardFileInputStream
                sodFile = SODFile(sodIn)
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
            }
            try {
                val comIn = service.getInputStream(PassportService.EF_COM) as CardFileInputStream
                comFile = COMFile(comIn)
            } catch (e: CardServiceException) {
                Log.e(TAG, e.toString())
            }

            val allFaceImageInfos: MutableList<FaceImageInfo> = ArrayList()
            val faceInfos = dg2File?.faceInfos
            faceInfos?.let {
                for (faceInfo in faceInfos) {
                    allFaceImageInfos.addAll(faceInfo.faceImageInfos)
                }
            }

            if (allFaceImageInfos.isNotEmpty()) {
                val faceImageInfo = allFaceImageInfos.iterator().next()
                val imageLength = faceImageInfo.imageLength
                val dataInputStream = DataInputStream(faceImageInfo.imageInputStream)
                val buffer = ByteArray(imageLength)
                dataInputStream.readFully(buffer, 0, imageLength)
                val inputStream: InputStream = ByteArrayInputStream(buffer, 0, imageLength)
                bitmap = JavaUtils.decodeImage(
                    this@ScanNFCActivity,
                    faceImageInfo.mimeType,
                    inputStream
                )
                withContext(Dispatchers.Main) { binding.dot4.isEnabled = false }
            }

            val mrzInfo = dg1File?.mrzInfo
            withContext(Dispatchers.Main) {
                val scannedData = ScannedData()
                mrzInfo?.let {
                    scannedData.apply {
                        name = mrzInfo.secondaryIdentifier.replace("<", " ").trim()
                        surname = mrzInfo.primaryIdentifier.replace("<", " ").trim()
                        birthDate = mrzInfo.dateOfBirth
                        gender = mrzInfo.gender.toString()
                        nationality = mrzInfo.nationality
                        docNumber = mrzInfo.documentNumber
                        docType = mrzInfo.documentCode
                        docExpiry = mrzInfo.dateOfExpiry
                    }
                }

                dg11File?.let {
                    val tokensStreet = StringTokenizer(it.permanentAddress[0], ",")
                    if (tokensStreet.countTokens() == 2) {
                        // we need only street and house number
                        val street = tokensStreet.nextToken()
                        val houseNumber = tokensStreet.nextToken()
                        scannedData.apply {
                            address = PermanentAddress(street, it.permanentAddress[1], it.permanentAddress[2])
                            this.houseNumber = houseNumber
                        }
                    } else {
                        scannedData.apply {
                            address = PermanentAddress(it.permanentAddress[0], it.permanentAddress[1], it.permanentAddress[2])
                        }
                    }
                    scannedData.apply {
                        fiscalCode = it.personalNumber
                        birthPlace = it.placeOfBirth[0]
                        birthProvince = it.placeOfBirth[1]
                        phoneNumber = it.telephone
                        it.fullDateOfBirth?.let { fdob ->
                            birthDate = fdob
                        }
                    }
                }

                val scaled = bitmap?.let {
                    var ratio = ImageUtil.getRatio(it.height, ImageUtil.MAX_HEIGHT)
                    var targetHeight = (it.height * ratio).toInt()
                    var targetWidth = (it.width * ratio).toInt()

                    // Check if width is under limit
                    if (targetWidth > ImageUtil.MAX_WIDTH) {
                        ratio = ImageUtil.getRatio(targetWidth, ImageUtil.MAX_WIDTH)
                        targetHeight = (targetHeight * ratio).toInt()
                        targetWidth = (targetWidth * ratio).toInt()
                    }

                    Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)
                }
                binding.dot5.isEnabled = false
                sendResults(scannedData, scaled)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                sendResults(null, null)
            }
            Log.e(TAG, e.toString())
        }
    }

    private fun promptFailure() {
        ScanUtils.createAlertDialog(
            context = this,
            title = getString(R.string.nfc_dialog_title_failure),
            message = getString(R.string.nfc_dialog_message_failure),
            positiveTitle = getString(R.string.nfc_dialog_retry),
            positiveListener = { dialog, which ->
                dialog.dismiss()
            },
            negativeTitle = getString(R.string.nfc_dialog_close),
            negativeListener = { dialog, which ->
                dialog.dismiss()
                sendFailure()
            }
        ).show()
    }

    private fun sendFailure() {
        val resultIntent = Intent()
        setResult(Activity.RESULT_CANCELED, resultIntent)
        finish()
    }

    private fun sendResults(scannedData: ScannedData?, bitmap: Bitmap?) {
        if (scannedData == null || bitmap == null) {
            promptFailure()
        } else {
            Log.d("RESULTS", scannedData.toString())
            val resultIntent = Intent()
            resultIntent.apply { putExtra(ScanUtils.NFC_RESULT_DATA, scannedData) }
            resultIntent.apply { putExtra(ScanUtils.NFC_RESULT_BITMAP, bitmap) }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            fullScreen()
        }
    }

    private fun fullScreen() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE
                )
    }

    companion object {
        private val TAG: String = ScanNFCActivity::class.java.simpleName
    }

    override fun onTagDiscovered(tag: Tag?) {
        vibrate()
        runOnUiThread {
            binding.loadingDots.isVisible=true
        }
        launch {
            val bacKey = BACKey(mrzInfo.documentNumber, mrzInfo.dateOfBirth, mrzInfo.dateOfExpiry)
            readData(IsoDep.get(tag), bacKey)
        }
    }
}
