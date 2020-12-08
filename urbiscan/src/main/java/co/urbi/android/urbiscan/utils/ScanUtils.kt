package co.urbi.android.urbiscan.utils

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.Fragment
import co.urbi.android.urbiscan.R
import co.urbi.android.urbiscan.camera.CameraXBarcodeScanActivity
import co.urbi.android.urbiscan.camera.CameraXMrzScanActivity
import co.urbi.android.urbiscan.camera.CameraXPortraitActivity
import co.urbi.android.urbiscan.nfc.ScanNFCActivity
import org.jmrtd.lds.icao.MRZInfo
import java.text.SimpleDateFormat
import java.util.*

class ScanUtils {
    companion object {

        const val NFC_RESULT_DATA = "urbiscan_nfc_data"
        const val NFC_RESULT_BITMAP = "urbiscan_nfc_bitmap"
        const val BARCODE_SCAN_RESULT = "barcode_scan_result"
        const val PORTRAIT_RESULT_BITMAP = "urbiscan_portrait_bitmap"
        const val SCANNED_MRZ_INFO = "scanned_mrz_info"
        const val DATEFORMAT_COMPACT = "yyMMdd"
        const val DATEFORMAT_EXTENDED = "yyyyMMdd"

        const val SCAN_CARD = 1
        const val SCAN_PASSPORT = 2
        const val SCAN_TYPE = "scan_type"
        const val IMMERSIVE_FLAG_TIMEOUT = 500L
        const val RESULT_RETRY = 2

        // https://www.doubango.org/SDKs/mrz/docs/MRZ_parser.html
        const val TD1_PATTERN = "([A|C|I][A-Z0-9<]{1})([A-Z]{3})([A-Z0-9<]{9})([0-9]{1})([A-Z0-9<]{15})\\n([0-9]{6})([0-9]{1})([M|F|X|<]{1})([0-9]{6})([0-9]{1})([A-Z]{3})([A-Z0-9<]{11})([0-9]{1})\\n([A-Z0-9<]{30})"
        val TD1_REGEX = TD1_PATTERN.toRegex()
        const val TD3_PATTERN = "(P[A-Z0-9<]{1})([A-Z]{3})([A-Z0-9<]{39})\\n([A-Z0-9<]{9})([0-9]{1})([A-Z]{3})([0-9]{6})([0-9]{1})([M|F|X|<]{1})([0-9]{6})([0-9]{1})([A-Z0-9<]{14})([0-9]{1})([0-9]{1})"
        val TD3_REGEX = TD3_PATTERN.toRegex()

        fun openCameraPortrait(fragment: Fragment? = null, activity: Activity? = null, requestCode: Int) {
            if (fragment != null) {
                val intent = Intent(fragment.requireContext(), CameraXPortraitActivity::class.java)
                fragment.startActivityForResult(intent, requestCode)
            } else if (activity != null) {
                val intent = Intent(activity, CameraXPortraitActivity::class.java)
                activity.startActivityForResult(intent, requestCode)
            }
        }

        fun openCameraScanCard(fragment: Fragment? = null, activity: Activity? = null, requestCode: Int) {
            if (fragment != null) {
                val intent = Intent(fragment.requireContext(), CameraXMrzScanActivity::class.java)
                intent.putExtra(SCAN_TYPE, SCAN_CARD)
                fragment.startActivityForResult(intent, requestCode)
            } else if (activity != null) {
                val intent = Intent(activity, CameraXMrzScanActivity::class.java)
                intent.putExtra(SCAN_TYPE, SCAN_CARD)
                activity.startActivityForResult(intent, requestCode)
            }
        }

        fun openCameraScanPassport(fragment: Fragment? = null, activity: Activity? = null, requestCode: Int) {
            if (fragment != null) {
                val intent = Intent(fragment.requireContext(), CameraXMrzScanActivity::class.java)
                intent.putExtra(SCAN_TYPE, SCAN_PASSPORT)
                fragment.startActivityForResult(intent, requestCode)
            } else if (activity != null) {
                val intent = Intent(activity, CameraXMrzScanActivity::class.java)
                intent.putExtra(SCAN_TYPE, SCAN_PASSPORT)
                activity.startActivityForResult(intent, requestCode)
            }
        }

        fun openScanNFC(fragment: Fragment, requestCode: Int) {
            fragment.startActivityForResult(Intent(fragment.requireContext(), ScanNFCActivity::class.java), requestCode)
        }

        fun openScanNFC(activity: Activity, requestCode: Int, mrzInfo: MRZInfo) {
            val intent = Intent(activity, ScanNFCActivity::class.java)
            intent.putExtra(SCANNED_MRZ_INFO, mrzInfo)
            activity.startActivityForResult(intent, requestCode)
        }

        fun openScanNFCFromCamera(activity: Activity, mrzInfo: MRZInfo, scanType: Int) {
            val intent = Intent(activity, ScanNFCActivity::class.java)
            intent.putExtra(SCANNED_MRZ_INFO, mrzInfo)
            intent.putExtra(SCAN_TYPE, scanType)
            intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            activity.startActivity(intent)
        }

        fun openScanNFC(activity: Activity, requestCode: Int) {
            activity.startActivityForResult(Intent(activity, ScanNFCActivity::class.java), requestCode)
        }

        fun openScanBarcode(context: Context, requestCode: Int) {
            if (context is Fragment)
                context.startActivityForResult(Intent(context, CameraXBarcodeScanActivity::class.java), requestCode)
            else if (context is Activity) {
                context.startActivityForResult(Intent(context, CameraXBarcodeScanActivity::class.java), requestCode)
            }
        }

        fun analyzeText(text: String, regex: Regex): MRZInfo? {
            Log.w("TEXT ANALYSIS", text)
            var textToAnalyze = ""
            if (text.length >= 30) { // TD1 has size 30, TD3 44, so it can't be less
                textToAnalyze = text.toUpperCase(Locale.getDefault()).replace(" ".toRegex(), "")
                if (textToAnalyze.contains(regex)) {
                    try {
                        val match = regex.find(textToAnalyze)
                        match?.let {
                            val mrzInfo = MRZInfo(it.value)
                            return mrzInfo
                        }
                    } catch (e: IllegalStateException) {
                        Log.w("TEXT ANALYSIS", "checksum fail", e)
                    } catch (e: IllegalArgumentException) {
                        Log.w("TEXT ANALYSIS", "checksum fail", e)
                    }
                }
            }
            return null
        }

        fun convertFormatDate(formattedData: String, formatFrom: String, formatTo: String): String? {
            val formatStart = SimpleDateFormat(formatFrom, Locale.getDefault())
            try {
                val date = formatStart.parse(formattedData)
                val longTime = date?.time
                val formatEnd = SimpleDateFormat(formatTo, Locale.getDefault())
                return formatEnd.format(longTime)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        fun convertFormattedDataToLong(formattedData: String, format: String): Long? {
            val formatting = SimpleDateFormat(format, Locale.getDefault())
            try {
                val date = formatting.parse(formattedData)
                return date?.time
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        fun createAlertDialog(
            context: Context,
            title: String,
            message: String,
            positiveTitle: String,
            positiveListener: DialogInterface.OnClickListener,
            negativeTitle: String? = null,
            negativeListener: DialogInterface.OnClickListener? = null
        ): AlertDialog {

            val builder = AlertDialog.Builder(context)
            builder.setMessage(message)
                .setTitle(title)
                .setCancelable(false)
                .setPositiveButton(positiveTitle, positiveListener)

            if (negativeTitle != null && negativeListener != null) {
                builder.setNegativeButton(negativeTitle, negativeListener)
            }

            return builder.create()
        }
    }
}
