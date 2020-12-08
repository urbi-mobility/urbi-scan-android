package co.urbi.android.urbiscan.utils

import co.urbi.android.urbiscan.utils.ScanUtils
import java.io.Serializable

data class ScannedData(
    var address: PermanentAddress? = null,
    var birthDate: String? = null, // yyMMdd or yyyyMMdd
    var birthPlace: String? = null,
    var birthProvince: String? = null,
    var cap: String? = null,
    var docExpiry: String? = null, // yyMMdd
    var docNumber: String? = null,
    var docType: String? = null,
    var domCap: String? = null,
    var domCity: String? = null,
    var domHouseNumber: String? = null,
    var domProvince: String? = null,
    var domStreet: String? = null,
    var email: String? = null,
    var fiscalCode: String? = null,
    var gender: String? = null,
    var houseNumber: String? = null,
    var name: String? = null,
    var nationality: String? = null,
    var phoneNumber: String? = null,
    var surname: String? = null
) : Serializable {
    fun getBirthDateDisplay(formatTo: String): String? {
        birthDate?.let {
            val formatFrom = if (it.length == 6) {
                ScanUtils.DATEFORMAT_COMPACT
            } else { // extended year
                ScanUtils.DATEFORMAT_EXTENDED
            }
            return ScanUtils.convertFormatDate(it, formatFrom, formatTo)
        }
        return null
    }

    fun getBirthDateLong(): Long? {
        birthDate?.let {
            val formatFrom = if (it.length == 6) {
                ScanUtils.DATEFORMAT_COMPACT
            } else { // extended year
                ScanUtils.DATEFORMAT_EXTENDED
            }
            return ScanUtils.convertFormattedDataToLong(it, formatFrom)
        }
        return null
    }

    fun getExpiryDateLong(): Long? {
        docExpiry?.let {
            return ScanUtils.convertFormattedDataToLong(it, ScanUtils.DATEFORMAT_COMPACT)
        }
        return null
    }

    fun setBirthDateString(date: String, formatFrom: String) {
        birthDate = ScanUtils.convertFormatDate(date, formatFrom, ScanUtils.DATEFORMAT_EXTENDED)
    }

    fun setExpiryDateString(date: String, formatFrom: String) {
        docExpiry = ScanUtils.convertFormatDate(date, formatFrom, ScanUtils.DATEFORMAT_EXTENDED)
    }
}

data class PermanentAddress(
    var street: String? = null,
    var city: String? = null,
    var province: String? = null
) : Serializable
