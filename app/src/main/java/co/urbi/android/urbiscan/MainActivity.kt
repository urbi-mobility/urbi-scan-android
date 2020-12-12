package co.urbi.android.urbiscan

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import co.urbi.android.urbiscan.utils.ScanUtils
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scanFace.setOnClickListener {
            ScanUtils.openCameraPortrait(activity = this, requestCode = 1)
        }

        scanCard.setOnClickListener {
            ScanUtils.openCameraScanCard(activity = this, requestCode = 2)
        }

        scanPassport.setOnClickListener {
            ScanUtils.openCameraScanPassport(activity = this, requestCode = 3)
        }

        scanBarcode.setOnClickListener {
            ScanUtils.openScanBarcode(this, 4)
        }

        scanText.setOnClickListener {

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK){
            when(requestCode){
                1 -> {

                }
                2 -> {

                }
                3 -> {

                }
                4 -> {

                }
            }
        } else {
            when(requestCode){
                1 -> {

                }
                2 -> {

                }
                3 -> {

                }
                4 -> {

                }
            }
        }
    }
}